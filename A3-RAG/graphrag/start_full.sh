#!/bin/bash

# GraphRAG 完整启动脚本
# 启动 Neo4j + 后端 API + 前端服务

echo "=========================================="
echo "  GraphRAG 完整启动脚本"
echo "=========================================="
echo ""

# 检查 Ollama
echo "[检查] 正在检查 Ollama 服务..."
if ! curl -s http://localhost:11434/api/tags &> /dev/null; then
    echo "⚠️  Ollama 服务未运行，请先启动:"
    echo "    ollama serve"
    echo ""
fi

# 启动 Neo4j
echo "[启动] 启动 Neo4j 服务器..."
neo4j start
sleep 5

# 安装后端依赖
echo "[安装] 检查后端依赖..."
pip install -r requirements.txt > /dev/null 2>&1

# 安装前端依赖
echo "[安装] 检查前端依赖..."
cd frontend && npm install > /dev/null 2>&1 && cd ..

# 启动后端服务（后台运行）
echo "[启动] 启动后端 API 服务..."
cd backend && python app.py &
BACKEND_PID=$!
cd ..

# 等待后端启动
sleep 3

# 启动前端服务（后台运行）
echo "[启动] 启动前端服务..."
cd frontend && npm start &
FRONTEND_PID=$!
cd ..

# 保存 PID 到文件
echo "$BACKEND_PID" > backend.pid
echo "$FRONTEND_PID" > frontend.pid

echo ""
echo "✅ 所有服务已启动！"
echo ""
echo "📊 服务地址:"
echo "   - 前端页面: http://localhost:3000"
echo "   - 后端 API: http://localhost:5000"
echo "   - Neo4j Browser: http://localhost:7474"
echo ""
echo "💡 停止服务: ./stop_full.sh"