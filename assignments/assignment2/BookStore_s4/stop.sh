#!/bin/bash

echo "======================================"
echo "   BookStore 项目关闭脚本"
echo "======================================"
echo ""

# 获取项目根目录
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# 查找并关闭后端服务
echo "🔍 查找后端服务..."
BACKEND_PID=$(lsof -ti :8080 2>/dev/null)
if [ -n "$BACKEND_PID" ]; then
    echo "   发现后端服务 (PID: $BACKEND_PID)"
    kill -15 $BACKEND_PID 2>/dev/null
    sleep 2
    
    # 检查是否成功关闭
    if kill -0 $BACKEND_PID 2>/dev/null; then
        echo "   强制关闭后端服务..."
        kill -9 $BACKEND_PID 2>/dev/null
    fi
    echo "   ✅ 后端服务已关闭"
else
    echo "   ℹ️  后端服务未运行"
fi
echo ""

# 查找并关闭前端服务
echo "🔍 查找前端服务..."
FRONTEND_PID=$(lsof -ti :5173 2>/dev/null)
if [ -n "$FRONTEND_PID" ]; then
    echo "   发现前端服务 (PID: $FRONTEND_PID)"
    kill -15 $FRONTEND_PID 2>/dev/null
    sleep 2
    
    # 检查是否成功关闭
    if kill -0 $FRONTEND_PID 2>/dev/null; then
        echo "   强制关闭前端服务..."
        kill -9 $FRONTEND_PID 2>/dev/null
    fi
    echo "   ✅ 前端服务已关闭"
else
    echo "   ℹ️  前端服务未运行"
fi
echo ""

# 清理日志文件（可选）
read -p "是否清理日志文件? (y/n): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    if [ -d "$PROJECT_ROOT/logs" ]; then
        rm -rf "$PROJECT_ROOT/logs"
        echo "   ✅ 日志文件已清理"
    fi
fi
echo ""

echo "======================================"
echo "   所有服务已关闭"
echo "======================================"
echo ""
