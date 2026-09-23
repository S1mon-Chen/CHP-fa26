#!/bin/bash

# 书籍查询系统停止脚本
# 停止所有服务器

echo "🛑 停止书籍查询系统..."
echo ""

# 查找并终止数据库版 FastMCP 服务器进程
echo "正在停止数据库版 FastMCP 服务器..."
MCP_DB_PIDS=$(pgrep -f "python3 hybrid_mcp.py")
if [ -n "$MCP_DB_PIDS" ]; then
    kill $MCP_DB_PIDS 2>/dev/null
    echo "✅ 数据库版 FastMCP 服务器已停止 (PID: $MCP_DB_PIDS)"
else
    echo "ℹ️  数据库版 FastMCP 服务器未运行"
fi

# 查找并终止 JSON 版 FastMCP 服务器进程
echo "正在停止 JSON 版 FastMCP 服务器..."
MCP_JSON_PIDS=$(pgrep -f "python3 json_mcp.py")
if [ -n "$MCP_JSON_PIDS" ]; then
    kill $MCP_JSON_PIDS 2>/dev/null
    echo "✅ JSON 版 FastMCP 服务器已停止 (PID: $MCP_JSON_PIDS)"
else
    echo "ℹ️  JSON 版 FastMCP 服务器未运行"
fi

# 查找并终止 HTTP 服务器进程
echo "正在停止 HTTP 服务器..."
HTTP_PIDS=$(pgrep -f "python3 -m http.server 3000")
if [ -n "$HTTP_PIDS" ]; then
    kill $HTTP_PIDS 2>/dev/null
    echo "✅ HTTP 服务器已停止 (PID: $HTTP_PIDS)"
else
    echo "ℹ️  HTTP 服务器未运行"
fi

# 等待进程结束
sleep 1

# 检查端口是否释放并强制清理
echo ""
echo "🔍 检查端口占用情况..."

# 检查 8000 端口
if lsof -i :8000 > /dev/null 2>&1; then
    echo "⚠️  端口 8000 仍被占用，强制释放..."
    lsof -ti :8000 | xargs kill -9 2>/dev/null
    echo "✅ 端口 8000 已释放"
else
    echo "✅ 端口 8000 已释放"
fi

# 检查 8001 端口
if lsof -i :8001 > /dev/null 2>&1; then
    echo "⚠️  端口 8001 仍被占用，强制释放..."
    lsof -ti :8001 | xargs kill -9 2>/dev/null
    echo "✅ 端口 8001 已释放"
else
    echo "✅ 端口 8001 已释放"
fi

# 检查 3000 端口
if lsof -i :3000 > /dev/null 2>&1; then
    echo "⚠️  端口 3000 仍被占用，强制释放..."
    lsof -ti :3000 | xargs kill -9 2>/dev/null
    echo "✅ 端口 3000 已释放"
else
    echo "✅ 端口 3000 已释放"
fi

echo ""
echo "=========================================="
echo "✨ 所有服务已停止！"
echo "=========================================="