#!/bin/bash

# GraphRAG Neo4j 版本启动脚本
# 功能：启动 Neo4j 服务器并运行 GraphRAG 程序

echo "=========================================="
echo "  GraphRAG Neo4j 版本启动脚本"
echo "=========================================="
echo ""

# 检查 Neo4j 是否已安装
if ! command -v neo4j &> /dev/null; then
    echo "❌ Neo4j 未安装，请先安装 Neo4j"
    exit 1
fi

# 检查 Ollama 是否运行
echo "[检查] 正在检查 Ollama 服务..."
if ! curl -s http://localhost:11434/api/tags &> /dev/null; then
    echo "⚠️  Ollama 服务未运行，请先启动 Ollama:"
    echo "    ollama serve"
    echo ""
fi

# 启动 Neo4j
echo "[启动] 启动 Neo4j 服务器..."
neo4j start

# 等待 Neo4j 启动
echo "[等待] 等待 Neo4j 服务器启动..."
sleep 5

# 检查 Neo4j 是否启动成功
echo "[检查] 检查 Neo4j 连接状态..."
if curl -s http://localhost:7474 &> /dev/null; then
    echo "✅ Neo4j 服务器启动成功"
else
    echo "❌ Neo4j 服务器启动失败，请检查日志"
    exit 1
fi

# 运行 GraphRAG 程序
echo ""
echo "[运行] 启动 GraphRAG 程序..."
echo "=========================================="
python local_graphrag_neo4j.py
echo "=========================================="
echo ""

# 提示 Neo4j Browser 地址
echo "📊 Neo4j 图形化界面地址: http://localhost:7474"
echo "   用户名: neo4j"
echo "   密码: reins2011!"
echo ""
echo "💡 执行 ./stop_graphrag.sh 停止服务"
