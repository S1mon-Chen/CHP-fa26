# 作业 6 Seata AT 分布式事务

学号：524031910668　姓名：陈熹朗

统一接口先通过 OpenFeign 调用库存服务，再调用订单服务。两个服务分别连接 `inventory_db` 与 `order_db`，各自提交本地事务，由 Apache Seata 2.5.0 协调全局提交或回滚。2026 年 11 月 7 日的真实验收中，28 项检查通过；异常场景库存从 98 变为 95，再自动恢复到 98，失败订单数始终为 0。

## 评分项与证据

| 评分项 | 实现 | 核验位置 |
| --- | --- | --- |
| 多库与 RPC | 三个 JVM，OpenFeign HTTP RPC，两个数据库及独立账号 | `compose.yml`、`infra/mysql/01-init.sql`、两个 Feign Client |
| 分布式事务组件 | Seata AT，自动代理数据源，XID 传播，两个 undo_log 表 | `CheckoutService`、`RpcConfiguration`、`XidFilter`、各服务配置 |
| 正常流程 | 全局提交后，库存减 2，订单增加 1 | `evidence/verification.json` 的 success 场景 |
| 异常回滚 | INSERT 后抛出 RuntimeException 子类，Seata 回滚库存 | rollback 场景及 `inventory-transactions.log` |
| 运行证据 | SQL 前中后快照，TC/RM 日志，真实浏览器截图 | `evidence/` |

## 架构与文件

```text
POST /api/orders
    checkout :18080  @GlobalTransactional
        | OpenFeign + TX_XID
        +--> inventory :18081  @Transactional --> inventory_db
        |       扣减库存并提交本地事务，保存 undo_log
        +--> orders :18082     @Transactional --> order_db
                插入订单；可在 INSERT 后模拟 RuntimeException

    三个服务 <------ TM / RM 协议 ------> Seata TC :18091
    MySQL :13306 上建立两个独立逻辑库；TC 使用持久化文件存储
```

- `common`：请求/响应契约、错误处理、下游 XID 绑定与清理。
- `checkout`：全局事务入口、OpenFeign 客户端、演示页面，无业务数据库。
- `inventory`：库存本地事务、行锁与条件扣减。
- `orders`：订单本地事务、请求号唯一约束、故障注入。
- `infra`：MySQL 建库建表、账户授权、Seata 服务端配置。
- `scripts`：一键启动、真实验收、截图、生成报告、精简打包。

所有 Java 文件在对应模块的 `src/main/java/com/bookstore/tx` 下。三个服务分别打包为可执行 JAR；JAR 只在运行时生成，不放入提交包。

## 为什么可以回滚另一个库

`CheckoutController` 调用独立 Spring Bean 的 `CheckoutService.place`，触发 `@GlobalTransactional(rollbackFor = Exception.class, timeoutMills = 30000)`。入口向 TC 申请全局 XID。Feign 请求拦截器将它放入 `TX_XID` 请求头；下游过滤器绑定到 `RootContext`，并在 finally 中清理，避免复用线程串事务。

库存与订单各自使用 `@Transactional`，数据源由 Seata starter 自动代理为 AT 模式，未重复配置手工代理。库存事务执行 `SELECT ... FOR UPDATE` 及带 `stock >= quantity` 条件的 UPDATE；业务修改与 undo_log 在同一个本地事务中提交。库存 Controller 在事务代理返回后记录 `INVENTORY_BRANCH_COMMITTED`，所以该日志对应的是已经提交的分支。

随后订单服务执行 INSERT。开启故障注入时抛出 `BusinessException`，它直接继承 `RuntimeException`。订单本地事务先回滚；HTTP 500 被 Feign 识别，入口重新抛出运行时异常，使全局事务进入回滚。TC 通知库存 RM，RM 根据 Undo 镜像恢复库存。业务源码中没有“库存加回来”的补偿 SQL。

订单异常发生后没有吞掉异常，也没有使用只覆盖单库的 `@Transactional` 冒充分布式事务。`pauseAfterInventoryMs` 仅用于在已提交的库存分支之后停留，便于 SQL 和浏览器观察；取消这个停留不改变事务机制。

## 运行

环境：JDK 21、Maven 3.9+、Docker Compose v2、Python 3。版本固定为 Spring Boot 3.5.16、Spring Cloud 2025.0.0、Seata 客户端/服务端 2.5.0、MySQL 8.4 系列。

```bash
# 从解压后的 BookStore_s4/assignment6 目录运行
export JAVA_HOME=/你的/JDK21/目录
bash scripts/run.sh
```

或从上一级 `BookStore_s4` 运行 `bash start.sh`。脚本检查应用端口，启动 Compose，执行 Maven 构建，再启动三个 JVM。服务全部就绪后输出页面地址：<http://127.0.0.1:18080/>。

| 组件 | 本机端口 | 数据与配置 |
| --- | ---: | --- |
| 统一入口 | 18080 | `checkout/src/main/resources/application.properties` |
| 库存服务 | 18081 | `inventory_db`，账号 `inventory_app` |
| 订单服务 | 18082 | `order_db`，账号 `order_app` |
| MySQL | 13306 | 独立 Compose 命名卷 `mysql-data` |
| Seata TC | 18091 | 独立 Compose 命名卷 `seata-data` |

演示账号密码均为代码中公开的本地测试配置，与原书店账户无关。服务与容器发布端口仅绑定 127.0.0.1。每个业务账号只授权自己的业务库。默认 application.properties 关闭故障注入，演示启动脚本显式设置 `DEMO_FAILURE_ENABLED=true`；可用 `DEMO_FAILURE_ENABLED=false bash scripts/run.sh` 禁用模拟异常和停留参数。

Ctrl+C 停止脚本启动的三个 JVM。MySQL 与 Seata 保留数据，停止它们使用：

```bash
docker compose stop
```

再次启动不会重置库存或订单。初始化 SQL 仅在新 MySQL 数据卷首次启动时运行。Seata 2.5 的 HTTP 与事务端口合并，本项目统一使用 18091，不依赖旧版 7091 控制台。

## 接口示例

```bash
curl -s http://127.0.0.1:18080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"demo-success-001","bookId":1,"quantity":2}'

curl -s http://127.0.0.1:18080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"demo-failure-001","bookId":1,"quantity":3,"failOrder":true,"pauseAfterInventoryMs":3000}'

curl -s http://127.0.0.1:18080/api/books
curl -s http://127.0.0.1:18080/api/orders/demo-failure-001
```

成功返回 HTTP 200，包含 `orderId`、`requestId`、`bookId`、`quantity`、`xid`、`replayed`。模拟异常返回 HTTP 500、错误码 `SIMULATED_ORDER_FAILURE` 与 XID。库存不足返回 409，图书不存在返回 404，字段校验失败返回 400。请求号只允许 1～64 位字母、数字、下划线和连字符；数量为 1～100000。

相同 requestId 与购买内容的已完成重试返回原订单，不再扣库存；同一请求号更换购买内容返回 409。订单库唯一键防止重复订单；Feign 禁止透明自动重试写请求。订单查询使用 `@GlobalLock`、本地事务及 `SELECT ... FOR UPDATE` 检查全局锁。发生连接超时等不确定结果时，应先按 requestId 查询，不能把失败响应当成“全局一定已经完成回滚”。

## 已执行的验收

```bash
python3 scripts/verify.py
```

脚本使用真实 HTTP 与 `docker compose exec ... mysql`，没有 Mock。它不会重置已有订单/库存，正常场景会消耗 2 本图书 1 的库存，并为并发测试新增专用图书。每次重新验证都会更新 evidence 内的验收输出；要保留本次结果，先复制 evidence。

| SQL 观察阶段 | 图书 1 库存 | 当前请求订单数 | 库存 Undo 数 |
| --- | ---: | ---: | ---: |
| 正常请求前 | 100 | 0 | 0 |
| 正常请求返回 | 98 | 1 | 1，随后异步清理为 0 |
| 异常请求前 | 98 | 0 | 0 |
| 异常请求中，库存已提交 | 95 | 0 | 1 |
| 异常请求回滚完成 | 98 | 0 | 0 |

以上为 2026-11-07 21:27（北京时间）的脚本实测；原始 JSON 时间使用 UTC。脚本核对异常响应 XID 与中间状态库存 Undo 的 XID 完全相同：`172.19.0.2:18091:5585305768673312772`。容器 IP、事务号和自增订单号会随环境改变。

28 项检查还覆盖三个服务可访问、分库授权、Undo 清理、已完成请求幂等、请求内容冲突、非法数量、缺失图书、库存不足、无 XID 写入拒绝、最后一本并发竞争。最后一本的两个并发请求分别返回 200 与 409，最终库存 0、订单 1。构建日志在 `evidence/build.log`，完整断言在 `verification.json`/`.txt`。

异常关键日志（完整内容见 evidence）：

```text
INVENTORY_BRANCH_COMMITTED ... before=98 after=95
SIMULATED_RUNTIME_EXCEPTION after INSERT ...
GLOBAL_ROLLBACK_TRIGGER ... cause=SIMULATED_ORDER_FAILURE
branch rollback success, xid:172.19.0.2:18091:5585305768673312772
Branch Rollbacked result: PhaseTwo_Rollbacked
rollback status: Rollbacked
```

真实浏览器另一次执行正常下单，库存 98→96；随后模拟失败，库存 96→94→96，订单未生成。对应 `browser-verification.json` 与 `01-success.png`、`02-deducted-before-failure.png`、`03-automatic-rollback.png`，浏览器无 JavaScript 异常。复现截图需要 Playwright 及 Chromium：

```bash
node scripts/capture.cjs
```

如果 Chromium 安装在自定义位置，可设置 `PLAYWRIGHT_CHROMIUM_EXECUTABLE`。浏览器截图工具是可选工具，不影响服务启动或 Python 验收。

## 边界

本次以单本图书的一次购买作为最小业务单元，单机运行三个微服务；保留原书店其他模块作为背景，不复用旧作业 5 的 Kafka 下单链路。Seata TC 使用单节点文件持久化，未声称具备高可用，也未进行主机断电、TC 重启或长时间网络分区的恢复测试。

AT 一阶段会本地提交，普通库存查询可读到全局完成前的库存值，本作业正利用这一点展示 98→95→98。生产中的所有库存写入都需要遵守相同的全局事务协议，绕过代理的直接 SQL 可能造成 Undo 镜像冲突。生产还需认证、密钥管理、故障告警与事务对账；演示用故障开关和公开测试密码不应直接用于公开服务。

## 提交包

```bash
python3 scripts/package.py
```

脚本按允许清单收录本次四个模块的 Java 源码/POM、配置、SQL、HTML、脚本、README、报告和证据，并写入 SHA-256 清单。排除整个旧工程、target、JAR、class、node_modules、.env、模型、运行环境和 Docker 数据。解压后的精简工程仍能独立构建运行。

已将生成的 ZIP 解压到独立临时目录，执行 `mvn -B clean package`，父工程与四个模块全部 BUILD SUCCESS。日志为 `evidence/submission-build.log`。此构建检查验证提交包完整性；28 项业务检查由前述真实 HTTP/MySQL 验收脚本执行。

## 参考资料

- Apache Seata 部署指南：<https://seata.apache.org/docs/ops/deploy-guide-beginner/>，用于核对自动数据源代理与 XID 传播。
- Seata 2.5 AT undo_log 定义：<https://github.com/apache/incubator-seata/blob/v2.5.0/script/client/at/db/mysql.sql>，用于核对回滚表字段。
- Seata 2.5 服务端配置：<https://github.com/apache/incubator-seata/blob/v2.5.0/server/src/main/resources/application.yml>。
- Seata 版本迁移说明：<https://seata.apache.org/docs/ops/upgrade/>，用于核对 2.5 服务端端口变化。
- Spring Cloud OpenFeign：<https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html>。
