# 作业 4 整合后的智能书店

学号：524031910668　姓名：陈熹朗

作业 4 已整合进 **`BookStore_s4`**。现有 Spring Boot 后端使用 Java 21 虚拟线程与 JWT 无状态认证，继续使用原数据库的用户、图书、订单和管理员权限；React 前端已同步支持 JWT 登录、刷新恢复、退出和失效处理。

所有数据库模式（包括 MySQL）默认在图书列表、详情和搜索的真实查询前执行 `Thread.sleep(200)`，模拟慢速数据库查询；延迟通过 `app.books.delay-ms` 配置。

**提交包：`524031910668-作业4.zip`**。包中包含本题相关源码、脚本、说明和实测证据，排除了 JAR、第三方依赖、数据库密码、运行密钥和原工程无关模块。包内 `MANIFEST.sha256` 可校验文件。

## 查看结果

- [整合说明与评分点对应](BookStore_s4/ASSIGNMENT4.md)
- [真实 401 和 200 截图](BookStore_s4/evidence-jwt/authentication-401-200.png)
- [原书店登录后的页面](BookStore_s4/evidence-jwt/bookstore-jwt-login.png)
- [通过 JWT 创建的订单](BookStore_s4/evidence-jwt/bookstore-jwt-order.png)

完整工程的 **85 项后端测试、19 项前端测试、11 项独立 HTTP 检查、9 项真实浏览器检查**全部通过。前端 lint、构建和复用同一份整合源码的精简构建也已通过。

## 在仓库里启动完整书店

需要 JDK 21、Maven、Node.js/npm 和 Python 3。进入 `assignments/assignment4/BookStore_s4`：

```bash
# JAVA_HOME 应指向自己的 JDK 21；脚本会优先使用它
bash scripts/run-assignment4.sh
# bash start.sh 也会进入相同的启动流程
```

首次构建需要联网下载依赖。脚本先运行完整后端测试，再启动 H2 演示后端和原 React 前端，Ctrl+C 停止这次启动的服务：

- 书店页面：http://127.0.0.1:5173
- 作业验证页：http://127.0.0.1:8080/verify.html
- 顾客：`student` / `bookstore-demo-2026`
- 管理员：`admin` / `admin-demo-2026`

这两个公开账号仅在 `assignment4` 演示 profile 初始化。演示使用隔离的 H2 数据库，重启后演示订单与新增用户清空。原 MySQL 模式继续使用数据库中的账号，不会自动建立这些演示账号；密码通过 `DB_PASSWORD` 环境变量配置。

本机验证使用的 JDK 路径为 `/tmp/assignment4-jdk21/Contents/Home`，临时目录仍存在时可以执行：

```bash
JAVA_HOME=/tmp/assignment4-jdk21/Contents/Home bash scripts/run-assignment4.sh
```

未提供 `JWT_SECRET` 时启动脚本临时生成随机密钥；重启后旧 Token 无效。固定密钥应在环境中配置为至少 32 字节随机数据的 Base64，源码和提交包均不保存运行密钥。

## 在作业 4 页面演示作业 2 工具

默认启动脚本现在同时启动模拟竞品服务（127.0.0.1:8091）。打开右下角“智能书店助手”，默认选中“库存与比价”；使用现有 `/api/ai/tool-chat` 和两个工具 Schema，无需切换到作业 2 工程。

- 点击“库存与比价示例”再发送：查询 ISBN 12345 的库存、本店价格和模拟竞品价格。
- 点击“ISBN 格式纠正示例”再发送：要求先传原始带连字符书号，观察 INVALID_ISBN、suggested_isbn 和后续纠正调用。
- 点击“无效 ISBN 示例”再发送：观察校验位错误及助手向用户确认，不能编造库存。
- 展开“查看查询记录”，展示每一轮函数名、原始参数、完整结果和错误。是否发生纠错以实际工具记录为准。

“图书推荐与政策”保留作业 3 的导购入口，需要下方 WITH_RAG 启动方式；两个模式分别保存本次页面中的对话历史。模型仍使用 `.env` 中配置的真实服务。竞品服务日志为 `logs/competitor.log`，默认后端工具日志为 `logs/assignment4-backend.log`。

如需演示超时重试，在 `.env` 设置 `COMPETITOR_TIMEOUT_ONCE=1` 后重启；每个 ISBN 首次竞品查询会超时，后续查询正常。演示完成后改回 0。核心 JWT 书店仍不需要模型密钥；两个聊天模式需要模型配置。

## 继续使用原 RAG 助手

```bash
WITH_RAG=1 bash scripts/run-assignment4.sh
```

该模式复用原向量服务、政策文档和模型配置，需要原有 Python 3.10–3.13 环境、向量依赖及 `.env` 中的大模型配置。`run-assignment2.sh`、`run-assignment3.sh` 和相关 HTTP 客户端也已适配 Java 21/JWT；此前作业目录中的文件不受此次整合影响。核心书店和作业 4 验证不需要大模型密钥。

## 从提交包独立运行

提交包按题目要求未包含整套 React/AI 工程。解压后进入包中的 `BookStore_s4`：

```bash
bash scripts/run-assignment4-submission.sh
```

这一入口使用 `assignment4/pom.xml`，**直接编译 `backend/src/main/java` 中已整合的同一份认证、用户和图书代码**，并运行 35 项真实 HTTP 测试。启动后打开 http://127.0.0.1:8084/verify.html。不需要数据库安装、Node.js 或原工程。

包中的前端文件及其测试是原工程的修改文件，可用于覆盖原 `BookStore_s4`；完整网页运行和订单回归证据来自仓库里的完整工程。

## 重新测试与打包

在 `BookStore_s4` 目录执行：

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build

# 完整后端启动在 8080 后
python3 scripts/verify_assignment4.py --output-dir evidence-new

# 同一后端的 100 并发 I/O 对照
python3 scripts/benchmark_assignment4.py --java "$JAVA_HOME/bin/java" --output evidence-new/benchmark.json

# 生成上级目录的最新提交包
python3 scripts/package_assignment4.py
```

仅有提交包时，使用 `mvn -f assignment4/pom.xml test` 和 `verify_assignment4.py --base-url http://127.0.0.1:8084`。浏览器可手动截图，自动截图及网页流程检查另需 Playwright/Chromium。

原 `bookstore-api` 目录作为初始版本保留。当前维护、运行、测试和打包入口都在 `BookStore_s4`。
