#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
source scripts/java21-env.sh
for command in java mvn npm python3; do
  command -v "$command" >/dev/null || { echo "Missing command: $command" >&2; exit 1; }
done
# Refuse occupied ports instead of shutting down another project.
python3 - <<'PY'
import socket
for port in (8080, 8091, 5173):
    with socket.socket() as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(('127.0.0.1', port))
        except OSError:
            raise SystemExit(f'Port {port} is occupied. Stop that service or run components separately.')
PY
mkdir -p logs
mvn -q -f backend/pom.xml -DskipTests package
if [[ ! -d frontend/node_modules ]]; then (cd frontend && npm ci); fi
pids=()
cleanup() {
  for pid in ${pids[@]+"${pids[@]}"}; do kill "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
if [[ "${COMPETITOR_TIMEOUT_ONCE:-0}" == "1" ]]; then
  python3 scripts/mock_competitor.py --timeout-once >logs/competitor.log 2>&1 &
else
  python3 scripts/mock_competitor.py >logs/competitor.log 2>&1 &
fi
pids+=("$!")
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo,assistant,assignment4 >logs/backend.log 2>&1 &
pids+=("$!")
(cd frontend && exec node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort) >logs/frontend.log 2>&1 &
pids+=("$!")
python3 - <<'PY'
import time, urllib.request
for attempt in range(60):
    try:
        for url in ['http://127.0.0.1:8080/api/ai/health', 'http://127.0.0.1:5173', 'http://127.0.0.1:8091/health']:
            urllib.request.urlopen(url, timeout=1).close()
        break
    except OSError:
        time.sleep(1)
else:
    raise SystemExit('Startup failed. Check logs/backend.log, logs/frontend.log and logs/competitor.log.')
PY
echo 'Ready: http://127.0.0.1:5173 — open the bottom-right assistant. Ctrl+C stops this demo.'
echo 'Tool Call and Tool Result traces: logs/backend.log'
while true; do
  for pid in "${pids[@]}"; do
    kill -0 "$pid" 2>/dev/null || { echo "A demo process exited; check logs." >&2; exit 1; }
  done
  sleep 2
done
