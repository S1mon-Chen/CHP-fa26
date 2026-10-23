#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ ! -x "$ROOT/.venv/bin/python" ]; then
    echo '请先安装 Python 3.12，然后在项目目录执行：'
    echo 'python3.12 -m venv .venv'
    echo '.venv/bin/python -m pip install -e ".[test]"'
    exit 1
fi
exec "$ROOT/.venv/bin/python" "$ROOT/scripts/classroom.py" start "$@"
