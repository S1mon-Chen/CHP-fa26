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

## 认证、AI 与技能 API

订单接口和技能接口现在需要登录；全量订单、销量、用户消费统计以及管理接口仅限管理员。客户端传入的 userId、role 不作为身份凭据。所有 POST/PUT/DELETE 请求（包括登录）必须携带 CSRF token。

前端使用 `/api` 相对地址，Vite 开发服务器代理到 `localhost:8080`。部署时应将 `/api` 反向代理到后端；若使用独立 API 域名，构建时设置 `VITE_API_BASE_URL`，并配置后端 `app.cors.allowed-origins` 为实际前端源，确保会话 Cookie 可以正常传递。

以下 curl 示例需替换登录信息，cookie 文件仅用于本次本地会话：

```bash
BOOKSTORE_COOKIE=$(mktemp)
BOOKSTORE_API=http://localhost:8080/api

# 取得匿名会话和 CSRF token
BOOKSTORE_CSRF=$(curl -fsS -b "$BOOKSTORE_COOKIE" -c "$BOOKSTORE_COOKIE" "$BOOKSTORE_API/auth/csrf" | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

# 替换为自己的账号密码
curl -fsS -b "$BOOKSTORE_COOKIE" -c "$BOOKSTORE_COOKIE" "$BOOKSTORE_API/auth/login" \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $BOOKSTORE_CSRF" \
  -d '{"username":"你的用户名","password":"你的密码"}'

# 登录会轮换会话和 token，重新获取后再执行写操作
BOOKSTORE_CSRF=$(curl -fsS -b "$BOOKSTORE_COOKIE" -c "$BOOKSTORE_COOKIE" "$BOOKSTORE_API/auth/csrf" | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
curl -fsS -b "$BOOKSTORE_COOKIE" "$BOOKSTORE_API/user/me"
curl -fsS -b "$BOOKSTORE_COOKIE" "$BOOKSTORE_API/skills/list"
curl -fsS -b "$BOOKSTORE_COOKIE" "$BOOKSTORE_API/skills/rules/list"

# 查询当前登录用户的订单，不能指定其他用户冒用身份
curl -fsS -b "$BOOKSTORE_COOKIE" "$BOOKSTORE_API/skills/OrderQuerySkill/execute" \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $BOOKSTORE_CSRF" \
  -d '{"input":""}'

# 退出并删除本次临时 cookie 文件
curl -fsS -b "$BOOKSTORE_COOKIE" -X POST "$BOOKSTORE_API/auth/logout" \
  -H "X-CSRF-TOKEN: $BOOKSTORE_CSRF"
rm "$BOOKSTORE_COOKIE"
```

AI 推荐只返回书籍信息和编号，不会创建订单。登录后明确发送 `下单 书籍编号 123` 可购买一本；`取消订单 123` 只能取消自己待支付的订单（管理员具有管理权限）。

## 回归检查

```bash
# 后端：Java 17，测试使用 H2，不需要连接现有 MySQL/Chroma
cd backend
mvn test

# 前端：安装锁定依赖后执行组件、请求层测试和构建
cd ../frontend
npm ci
npm test
npm run lint
npm run build
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

- Node.js 22.22.2+、24.15+ 或 26+（本次验证使用 Node.js 26）
- Maven >= 3.6
- Java >= 17
- MySQL >= 8.0
