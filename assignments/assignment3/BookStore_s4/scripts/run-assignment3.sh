#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
for cmd in java mvn npm; do
  command -v "$cmd" >/dev/null || { echo "Missing command: $cmd" >&2; exit 1; }
done
if [[ ! -x .venv-rag/bin/python ]]; then
  rag_python="${RAG_PYTHON:-}"
  if [[ -z "$rag_python" ]]; then
    for candidate in python3.12 python3.13 python3.11 python3; do
      if command -v "$candidate" >/dev/null && "$candidate" -c 'import sys; assert (3,10) <= sys.version_info[:2] < (3,14)' 2>/dev/null; then
        rag_python="$candidate"
        break
      fi
    done
  fi
  [[ -n "$rag_python" ]] || { echo 'Install Python 3.10–3.13 or set RAG_PYTHON to its executable.' >&2; exit 1; }
  "$rag_python" -m venv .venv-rag
fi
.venv-rag/bin/python - <<'PY'
import socket
for port in (8080, 8092, 5173):
    with socket.socket() as sock:
        try:
            sock.bind(('127.0.0.1', port))
        except OSError:
            raise SystemExit(f'Port {port} is occupied. Stop the conflicting service first.')
PY
if ! .venv-rag/bin/python -c 'import fastembed, onnxruntime' 2>/dev/null; then
  .venv-rag/bin/python -m pip install -r requirements-rag.txt
fi
mkdir -p logs
mvn -q -f backend/pom.xml -DskipTests package
if [[ ! -d frontend/node_modules/react-markdown || ! -d frontend/node_modules/remark-gfm ]]; then (cd frontend && npm ci); fi
pids=()
cleanup() {
  for pid in ${pids[@]+"${pids[@]}"}; do kill "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
.venv-rag/bin/python scripts/embedding_server.py >logs/embedding.log 2>&1 &
pids+=("$!")
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo,assistant,shopping --server.address=127.0.0.1 >logs/shopping-backend.log 2>&1 &
pids+=("$!")
(cd frontend && exec node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort) >logs/shopping-frontend.log 2>&1 &
pids+=("$!")
echo 'Starting. The first run downloads the Chinese BGE model into .model-cache/ (about 90 MB).'
.venv-rag/bin/python - <<'PY'
import time, urllib.request
for attempt in range(300):
    try:
        for url in ['http://127.0.0.1:8092/health', 'http://127.0.0.1:8080/api/ai/shopping/index', 'http://127.0.0.1:5173']:
            urllib.request.urlopen(url, timeout=5).close()
        break
    except OSError:
        time.sleep(1)
else:
    raise SystemExit('Startup failed; inspect logs/embedding.log and logs/shopping-backend.log.')
PY
echo 'Ready: http://127.0.0.1:5173 — bottom-right assistant. Ctrl+C stops this demo.'
echo 'ReAct traces: logs/shopping-backend.log'
while true; do
  for pid in "${pids[@]}"; do
    kill -0 "$pid" 2>/dev/null || { echo 'A demo process exited; inspect logs/.' >&2; exit 1; }
  done
  sleep 2
done
