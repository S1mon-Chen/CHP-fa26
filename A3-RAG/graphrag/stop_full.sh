#!/bin/bash

# GraphRAG 完整关闭脚本
# 停止 Neo4j + 后端 API + 前端服务

echo "=========================================="
echo "  GraphRAG 完整关闭脚本"
echo "=========================================="
echo ""

# 停止后端服务
if [ -f backend.pid ]; then
    BACKEND_PID=$(cat backend.pid)
    echo "[停止] 停止后端 API 服务 (PID: $BACKEND_PID)..."
    kill $BACKEND_PID 2>/dev/null || true
    rm backend.pid
fi

# 停止前端服务
if [ -f frontend.pid ]; then
    FRONTEND_PID=$(cat frontend.pid)
    echo "[停止] 停止前端服务 (PID: $FRONTEND_PID)..."
    kill $FRONTEND_PID 2>/dev/null || true
    rm frontend.pid
fi

# 停止 Neo4j
echo "[停止] 停止 Neo4j 服务器..."
neo4j stop

echo ""
echo "✅ 所有服务已停止！"