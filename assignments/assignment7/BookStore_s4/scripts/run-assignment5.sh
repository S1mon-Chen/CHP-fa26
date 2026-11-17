#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null) || true
  export JAVA_HOME
fi
source scripts/java21-env.sh
for command in mvn docker python3; do
  command -v "$command" >/dev/null || { echo "Missing command: $command" >&2; exit 1; }
done
python3 - <<'PY'
import socket
with socket.socket() as sock:
    try: sock.bind(('127.0.0.1',8080))
    except OSError: raise SystemExit('端口 8080 已占用，请先停止对应服务。')
PY
# Only start the React app when its complete source is available (the submission has a self-contained demo page).
with_frontend=0
if [[ -f frontend/package.json && "${WITH_FRONTEND:-1}" == 1 ]]; then with_frontend=1; fi
if [[ "$with_frontend" == 1 ]]; then
  command -v npm >/dev/null
  python3 - <<'PY'
import socket
with socket.socket() as sock:
    try: sock.bind(('127.0.0.1',5173))
    except OSError: raise SystemExit('端口 5173 已占用，请先停止对应服务。')
PY
fi
# No unrelated Docker services or volumes are stopped/removed by this script.
docker compose -f compose.assignment5.yml up -d --wait
if [[ -f backend/pom.xml ]]; then
  mvn -B -f backend/pom.xml package
  jar=backend/target/backend-0.0.1-SNAPSHOT.jar
else
  mvn -B -f assignment5/pom.xml package
  jar=assignment5/target/assignment5-integrated-1.0.0.jar
fi
if [[ "$with_frontend" == 1 && ! -d frontend/node_modules ]]; then npm --prefix frontend ci; fi
mkdir -p logs
pids=()
cleanup() {
  for pid in ${pids[@]+"${pids[@]}"}; do kill "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
java -jar "$jar" --spring.profiles.active=demo,assignment4,assignment5 >logs/assignment5-backend.log 2>&1 &
pids+=("$!")
if [[ "$with_frontend" == 1 ]]; then
  (cd frontend && exec node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort) >logs/assignment5-frontend.log 2>&1 &
  pids+=("$!")
fi
python3 - <<'PY'
import time, urllib.request
client=urllib.request.build_opener(urllib.request.ProxyHandler({}))
for _ in range(60):
    try:
        client.open('http://127.0.0.1:8080/orders-demo.html',timeout=1).close(); break
    except OSError: time.sleep(1)
else: raise SystemExit('后端启动失败，见 logs/assignment5-backend.log')
PY
echo '作业 5 演示：http://127.0.0.1:8080/orders-demo.html'
if [[ "$with_frontend" == 1 ]]; then echo '完整书店：http://127.0.0.1:5173'; fi
echo '账号 student / bookstore-demo-2026；Ctrl+C 关闭本次 Java/前端服务。'
echo 'Kafka 保持运行；停止命令：docker compose -f compose.assignment5.yml stop kafka'
while true; do
  for pid in "${pids[@]}"; do kill -0 "$pid" 2>/dev/null || exit 1; done
  sleep 2
done
