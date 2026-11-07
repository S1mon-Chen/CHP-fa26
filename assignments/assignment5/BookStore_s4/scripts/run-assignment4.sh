#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Optional complete RAG stack keeps the previous assignment's vector service and assistant.
if [[ "${WITH_RAG:-0}" == 1 ]]; then exec bash scripts/run-assignment3.sh; fi
if [[ -f .env ]]; then set -a; source .env; set +a; fi
source scripts/java21-env.sh
for command in mvn node npm python3; do
  command -v "$command" >/dev/null || { echo "Missing command: $command" >&2; exit 1; }
done
python3 - <<'PY'
import socket
for port in (8080, 8091, 5173):
    with socket.socket() as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try: sock.bind(('127.0.0.1', port))
        except OSError: raise SystemExit(f'Port {port} is occupied; stop that service first.')
PY
mvn -B -f backend/pom.xml package
if [[ ! -d frontend/node_modules ]]; then (cd frontend && npm ci); fi
mkdir -p logs
pids=()
cleanup() {
  for pid in ${pids[@]+"${pids[@]}"}; do kill "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
competitor_args=()
if [[ "${COMPETITOR_TIMEOUT_ONCE:-0}" == "1" ]]; then competitor_args+=(--timeout-once); fi
python3 scripts/mock_competitor.py "${competitor_args[@]+"${competitor_args[@]}"}" >logs/competitor.log 2>&1 &
pids+=("$!")
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=demo,assistant,assignment4 >logs/assignment4-backend.log 2>&1 &
pids+=("$!")
(cd frontend && exec node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort) \
  >logs/assignment4-frontend.log 2>&1 &
pids+=("$!")
python3 - <<'PY'
import time, urllib.request
client = urllib.request.build_opener(urllib.request.ProxyHandler({}))
for _ in range(90):
    try:
        for url in ('http://127.0.0.1:8080/verify.html', 'http://127.0.0.1:5173', 'http://127.0.0.1:8091/health'):
            client.open(url, timeout=1).close()
        break
    except OSError: time.sleep(1)
else: raise SystemExit('Startup failed; inspect logs/assignment4-*.log.')
PY
echo '书店：http://127.0.0.1:5173  验证：http://127.0.0.1:8080/verify.html'
echo '顾客 student / bookstore-demo-2026；管理员 admin / admin-demo-2026'
echo 'Ctrl+C 停止服务。启用原 RAG 功能：WITH_RAG=1 bash scripts/run-assignment4.sh'
while true; do
  for pid in "${pids[@]}"; do kill -0 "$pid" 2>/dev/null || exit 1; done
  sleep 2
done
