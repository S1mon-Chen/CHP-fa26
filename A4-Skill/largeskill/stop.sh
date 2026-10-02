#!/bin/bash

# Ensure we run from the script directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

PID_FILE=".streamlit_server.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null; then
        echo "Stopping Streamlit server (PID: $PID)..."
        kill $PID
        # Wait a bit to ensure it stopped
        sleep 1
        if ps -p $PID > /dev/null; then
            echo "Force stopping Streamlit server..."
            kill -9 $PID
        fi
        echo "Streamlit server stopped."
    else
        echo "Streamlit server process (PID: $PID) is not running."
    fi
    rm "$PID_FILE"
else
    echo "No Streamlit server PID file found ($PID_FILE). Is it running?"
    # Fallback to search process using ps
    PID=$(ps aux | grep "streamlit run app.py" | grep -v grep | awk '{print $2}')
    if [ ! -z "$PID" ]; then
        echo "Found Streamlit process (PID: $PID) running without PID file. Stopping..."
        kill $PID
        echo "Streamlit server stopped."
    else
        echo "No running Streamlit server found."
    fi
fi
