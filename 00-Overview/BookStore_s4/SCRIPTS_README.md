# BookStore 项目管理脚本

## 快速使用

### 启动项目
```bash
./start.sh
```

### 关闭项目
```bash
./stop.sh
```

## 脚本功能说明

### start.sh - 启动脚本

**功能**：
- 自动检查端口占用情况
- 启动后端服务（Spring Boot，端口 8080）
- 启动前端服务（Vite，端口 5173）
- 等待服务就绪并显示访问地址
- 日志输出到 `logs/` 目录

**输出**：
- 后端日志：`logs/backend.log`
- 前端日志：`logs/frontend.log`

### stop.sh - 关闭脚本

**功能**：
- 自动查找并关闭后端服务
- 自动查找并关闭前端服务
- 可选清理日志文件
- 优雅关闭（SIGTERM），必要时强制关闭（SIGKILL）

## 访问地址

启动成功后：

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端界面 | http://localhost:5173/ | 用户界面 |
| 后端API | http://localhost:8080/ | REST API |

## 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | password | 管理员 |
| user | password | 普通用户 |

## 技能系统API

```bash
# 查看技能列表
curl http://localhost:8080/api/skills/list

# 查看规则列表
curl http://localhost:8080/api/skills/rules/list

# 查看技能统计
curl http://localhost:8080/api/skills/statistics

# 执行书籍搜索技能
curl -X POST http://localhost:8080/api/skills/BookSearchSkill/execute \
  -H "Content-Type: application/json" \
  -d '{"input":"java","context":{}}'

# 执行订单查询技能
curl -X POST http://localhost:8080/api/skills/OrderQuerySkill/execute \
  -H "Content-Type: application/json" \
  -d '{"input":"","context":{"userId":"1"}}'

# 执行促销查询技能
curl -X POST http://localhost:8080/api/skills/PromotionQuerySkill/execute \
  -H "Content-Type: application/json" \
  -d '{"input":"","context":{}}'

# 评估规则
curl -X POST http://localhost:8080/api/skills/rules/UserLoggedInRule/evaluate \
  -H "Content-Type: application/json" \
  -d '{"userId":"1"}'
```

## 常见问题

### Q: 端口被占用怎么办？
A: 运行 `./stop.sh` 关闭现有服务，然后重新运行 `./start.sh`

### Q: 如何查看日志？
A: 日志文件位于 `logs/` 目录下：
- `tail -f logs/backend.log` - 查看后端日志
- `tail -f logs/frontend.log` - 查看前端日志

### Q: 服务启动失败怎么办？
A: 
1. 检查日志文件查看错误信息
2. 确认 MySQL 数据库已启动
3. 确认端口 8080 和 5173 未被占用
4. 检查 Node.js 和 Maven 是否正确安装

## 系统要求

- Node.js >= 16
- Maven >= 3.6
- Java >= 17
- MySQL >= 8.0
