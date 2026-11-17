#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
for executable in java mvn docker python3; do command -v "$executable" >/dev/null; done
python3 - <<'PY'
import os,socket
with socket.socket() as sock:
    try: sock.bind(('127.0.0.1',int(os.getenv('PORT','18087'))))
    except OSError: raise SystemExit('应用端口已被占用，请先停止对应进程。')
PY
docker compose up -d --wait
mkdir -p logs
if [[ "${SKIP_BUILD:-0}" != 1 ]]; then mvn -B package; fi
export DEMO_ENABLED=true
runtime_dir=$(mktemp -d "$PWD/logs/runtime.XXXXXX")
cp target/assignment7-1.0.0.jar "$runtime_dir/application.jar"
java -Xms64m -Xmx256m -Dcsp.sentinel.log.dir="$PWD/logs/sentinel" \
  -jar "$runtime_dir/application.jar" >logs/application.log 2>&1 &
app_pid=$!
cleanup() {
  kill "$app_pid" 2>/dev/null || true
  wait "$app_pid" 2>/dev/null || true
  rm -f "$runtime_dir/application.jar"
  rmdir "$runtime_dir" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
python3 - <<'PY'
import os,time,urllib.request
client=urllib.request.build_opener(urllib.request.ProxyHandler({}))
url=f"http://127.0.0.1:{os.getenv('PORT','18087')}/demo/health"
for _ in range(60):
    try:
        with client.open(url,timeout=2) as r: print(r.read().decode()); break
    except OSError: time.sleep(1)
else: raise SystemExit('启动失败，详见 logs/application.log')
PY
echo "作业 7：http://127.0.0.1:${PORT:-18087}/"
echo '验收：python3 scripts/verify.py --outage'
echo 'Ctrl+C 停止应用；docker compose stop 停止本作业的数据库和缓存。'
wait "$app_pid"
