#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null) || true
  export JAVA_HOME
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
for executable in java mvn docker python3; do command -v "$executable" >/dev/null; done
java_version=$(java -version 2>&1 | head -n 1)
if [[ "$java_version" != *'"21.'* ]]; then echo '请设置 JAVA_HOME 为 JDK 21 的目录。' >&2; exit 1; fi
python3 - <<'PY'
import socket
for port in (18080,18081,18082):
    with socket.socket() as sock:
        try: sock.bind(('127.0.0.1',port))
        except OSError: raise SystemExit(f'端口 {port} 已占用，请先关闭对应服务。')
PY
docker compose up -d --wait mysql seata
python3 - <<'PY'
import socket,time
for _ in range(90):
    try:
        with socket.create_connection(('127.0.0.1',18091),1): break
    except OSError: time.sleep(1)
else: raise SystemExit('Seata 启动失败，请检查 docker compose logs seata')
PY
if [[ "${SKIP_BUILD:-0}" != 1 ]]; then mvn -B package; fi
mkdir -p logs
pids=()
cleanup() {
  for pid in ${pids[@]+"${pids[@]}"}; do kill "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
# Fault injection is explicitly enabled by this LOCAL demonstration launcher.
export DEMO_FAILURE_ENABLED=${DEMO_FAILURE_ENABLED:-true}
for service in inventory orders checkout; do
  java -Xms64m -Xmx256m -jar "$service/target/tx-$service-1.0.0.jar" >"logs/$service.log" 2>&1 &
  pids+=("$!")
done
python3 - <<'PY'
import time,urllib.request
client=urllib.request.build_opener(urllib.request.ProxyHandler({}))
for port in (18081,18082,18080):
    for _ in range(90):
        try:
            client.open(f'http://127.0.0.1:{port}/health',timeout=1).close(); break
        except OSError: time.sleep(1)
    else: raise SystemExit(f'服务 {port} 启动失败，见 logs 目录')
PY
echo '作业 6：http://127.0.0.1:18080/ ；验证：python3 scripts/verify.py'
echo 'Ctrl+C 停止本次 Java 进程；数据库及 Seata 保留，使用 docker compose stop 停止。'
while true; do
  for pid in "${pids[@]}"; do kill -0 "$pid" 2>/dev/null || exit 1; done
  sleep 2
done
