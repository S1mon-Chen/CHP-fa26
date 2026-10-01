#!/bin/bash

echo "======================================"
echo "   BookStore 项目启动脚本"
echo "======================================"
echo ""

# 获取项目根目录
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# 检查端口是否被占用
check_port() {
    local port=$1
    if lsof -i :$port >/dev/null 2>&1; then
        return 0  # 端口被占用
    else
        return 1  # 端口空闲
    fi
}

# 检查后端端口
if check_port 8080; then
    echo "⚠️  后端端口 8080 已被占用"
    echo "   如果需要重启，请先运行 ./stop.sh"
    echo ""
else
    echo "🚀 启动后端服务..."
    cd "$PROJECT_ROOT/backend"
    mvn spring-boot:run > "$PROJECT_ROOT/logs/backend.log" 2>&1 &
    BACKEND_PID=$!
    echo "   后端服务启动中... (PID: $BACKEND_PID)"
    echo "   日志文件: logs/backend.log"
    
    # 等待后端启动
    echo "   等待后端服务就绪..."
    for i in {1..30}; do
        if curl -s http://localhost:8080/actuator/health >/dev/null 2>&1 || curl -s http://localhost:8080/api/books >/dev/null 2>&1; then
            echo "   ✅ 后端服务已就绪"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "   ⚠️  后端服务启动超时，请检查日志"
        fi
        sleep 1
    done
    echo ""
fi

# 检查前端端口
if check_port 5173; then
    echo "⚠️  前端端口 5173 已被占用"
    echo "   如果需要重启，请先运行 ./stop.sh"
    echo ""
else
    echo "🚀 启动前端服务..."
    cd "$PROJECT_ROOT/frontend"
    npm run dev > "$PROJECT_ROOT/logs/frontend.log" 2>&1 &
    FRONTEND_PID=$!
    echo "   前端服务启动中... (PID: $FRONTEND_PID)"
    echo "   日志文件: logs/frontend.log"
    
    # 等待前端启动
    sleep 3
    echo "   ✅ 前端服务已就绪"
    echo ""
fi

echo "======================================"
echo "   启动完成！"
echo "======================================"
echo ""
echo "访问地址："
echo "  📱 前端界面: http://localhost:5173/"
echo "  🔧 后端API:  http://localhost:8080/"
echo ""
echo "测试账号："
echo "  管理员: admin / password"
echo "  普通用户: user / password"
echo ""
echo "技能系统API："
echo "  技能列表: http://localhost:8080/api/skills/list"
echo "  规则列表: http://localhost:8080/api/skills/rules/list"
echo "  技能统计: http://localhost:8080/api/skills/statistics"
echo ""
echo "停止服务请运行: ./stop.sh"
echo ""
