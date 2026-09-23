#!/bin/bash

# 书籍查询系统启动脚本
# 启动两个 FastMCP 服务器和 HTTP 服务器

echo "🚀 启动书籍查询系统..."
echo ""

# 进入项目目录
cd "$(dirname "$0")"

# 检查 Python 是否安装
if ! command -v python3 &> /dev/null; then
    echo "❌ 错误：未找到 Python3，请先安装 Python3"
    exit 1
fi

echo "✅ Python 版本：$(python3 --version)"
echo ""

# 启动数据库版 FastMCP 服务器（后台运行）
echo "📡 启动数据库版 FastMCP 服务器..."
echo "   地址：http://127.0.0.1:8000/mcp"
python3 hybrid_mcp.py &
MCP_DB_PID=$!

# 等待服务器启动
sleep 2

# 检查服务器是否启动成功
if ps -p $MCP_DB_PID > /dev/null; then
    echo "✅ 数据库版 FastMCP 服务器已启动 (PID: $MCP_DB_PID)"
else
    echo "❌ 数据库版 FastMCP 服务器启动失败"
    exit 1
fi

echo ""

# 启动 JSON 版 FastMCP 服务器（后台运行）
echo "📚 启动 JSON 版 FastMCP 服务器..."
echo "   地址：http://127.0.0.1:8001/mcp"
python3 json_mcp.py &
MCP_JSON_PID=$!

# 等待服务器启动
sleep 2

# 检查服务器是否启动成功
if ps -p $MCP_JSON_PID > /dev/null; then
    echo "✅ JSON 版 FastMCP 服务器已启动 (PID: $MCP_JSON_PID)"
else
    echo "❌ JSON 版 FastMCP 服务器启动失败"
    kill $MCP_DB_PID
    exit 1
fi

echo ""

# 启动 HTTP 服务器（后台运行）
echo "🌐 启动 HTTP 服务器..."
echo "   地址：http://localhost:3000/index.html"
python3 -m http.server 3000 &
HTTP_PID=$!

# 等待服务器启动
sleep 1

# 检查服务器是否启动成功
if ps -p $HTTP_PID > /dev/null; then
    echo "✅ HTTP 服务器已启动 (PID: $HTTP_PID)"
else
    echo "❌ HTTP 服务器启动失败"
    kill $MCP_DB_PID $MCP_JSON_PID
    exit 1
fi

echo ""
echo "=========================================="
echo "✨ 所有服务已启动成功！"
echo ""
echo "📊 服务信息:"
echo "   数据库版 MCP：http://127.0.0.1:8000/mcp"
echo "   JSON 版 MCP：http://127.0.0.1:8001/mcp"
echo "   Web 界面：http://localhost:3000/index.html"
echo ""
echo "🛑 停止服务方法:"
echo "   按 Ctrl+C 或运行：./stop_servers.sh"
echo "=========================================="
echo ""

# 等待用户中断
wait