#!/bin/bash

# Ensure we run from the script directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

PID_FILE=".streamlit_server.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null; then
        echo "Streamlit server is already running (PID: $PID)."
        echo "Access it at: http://localhost:8501"
        exit 0
    else
        rm "$PID_FILE"
    fi
fi

# Find Python or Streamlit executable
PYTHON_BIN=""

for p in python3 /opt/anaconda3/bin/python3 /opt/homebrew/bin/python3 /usr/local/bin/python3 "$HOME/miniconda3/bin/python3" "$HOME/anaconda3/bin/python3"; do
    if command -v "$p" >/dev/null 2>&1 || [ -x "$p" ]; then
        if "$p" -c "import streamlit" >/dev/null 2>&1; then
            PYTHON_BIN="$p"
            break
        fi
    fi
done

if [ -n "$PYTHON_BIN" ]; then
    RUN_CMD=("$PYTHON_BIN" "-m" "streamlit" "run" "app.py" "--server.port" "8501")
elif command -v streamlit >/dev/null 2>&1; then
    RUN_CMD=("streamlit" "run" "app.py" "--server.port" "8501")
elif [ -x "/opt/anaconda3/bin/streamlit" ]; then
    RUN_CMD=("/opt/anaconda3/bin/streamlit" "run" "app.py" "--server.port" "8501")
else
    echo "❌ 错误: 未检测到安装了 streamlit 的 Python 环境！"
    echo "请先在您的 Python 环境中运行: pip install streamlit"
    exit 1
fi

echo "Starting Streamlit dashboard in the background using ${RUN_CMD[0]}..."
PYTHONPATH=.agent/skills "${RUN_CMD[@]}" > streamlit.log 2>&1 &
NEW_PID=$!

echo $NEW_PID > "$PID_FILE"

# Wait briefly and verify process is healthy
sleep 1.5
if ps -p $NEW_PID > /dev/null; then
    echo "✅ Streamlit server started successfully (PID: $NEW_PID)."
    echo "Log file: streamlit.log"
    echo "Access it at: http://localhost:8501"
else
    echo "❌ Streamlit server failed to start! Check streamlit.log:"
    cat streamlit.log
    rm -f "$PID_FILE"
    exit 1
fi
