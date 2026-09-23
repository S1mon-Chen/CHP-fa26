#!/bin/bash

# GraphRAG Neo4j 版本关闭脚本
# 功能：停止 Neo4j 服务器

echo "=========================================="
echo "  GraphRAG Neo4j 版本关闭脚本"
echo "=========================================="
echo ""

# 检查 Neo4j 是否已安装
if ! command -v neo4j &> /dev/null; then
    echo "❌ Neo4j 未安装"
    exit 1
fi

# 停止 Neo4j
echo "[停止] 正在停止 Neo4j 服务器..."
neo4j stop

# 等待停止完成
sleep 3

# 检查是否停止成功
echo "[检查] 检查 Neo4j 状态..."
if curl -s http://localhost:7474 &> /dev/null; then
    echo "⚠️  Neo4j 可能仍在运行中"
else
    echo "✅ Neo4j 服务器已停止"
fi

echo ""
echo "📝 如需重新启动，请运行: ./start_graphrag.sh"
