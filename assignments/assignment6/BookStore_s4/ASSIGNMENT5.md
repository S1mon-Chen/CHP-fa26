# 作业 5 Kafka 异步订单与 SSE 推送

学号：524031910668　姓名：陈熹朗

本作业在现有 `BookStore_s4` 上加入异步订单。用户提交购物车后，接口向真实 Kafka 投递订单 JSON，收到 Broker 确认后返回 `202 Accepted` 与 `queueId`。Kafka 消费者模拟 3 秒库存操作，在数据库事务中创建订单并扣减库存，然后通过 Spring `SseEmitter` 推送成功或失败。

## 评分点对应

| 评分项 | 实现 | 验证证据 |
| --- | --- | --- |
| KafkaProducer 发送订单事件 | `queue/OrderQueueService.java` 使用 `KafkaTemplate<String,String>` 向 `order-events` 发送 JSON，设置 `acks=all` 和生产幂等性 | `evidence-kafka/kafka-messages.txt`、`producer-consumer.log` |
| KafkaConsumer 异步处理 | `queue/OrderEventConsumer.java` 的 `@KafkaListener`，3 个消费者线程，关闭自动提交，逐条处理后提交位点 | `kafka-topic.txt`、`kafka-consumer-group.txt` |
| Spring SseEmitter | `OrderController.stream` 与 `queue/OrderStreams.java`，`GET /api/orders/{queueId}/stream` | `success-stream.txt`、`failure-stream.txt` |
| 消费结果回调 | 数据库事务提交后调用 `streams.publish`，发送 `SUCCEEDED` 或 `FAILED`，结束连接 | `http-verification.json`、成功/失败截图 |
| 非阻塞全流程截图 | 202 排队时页面仍可交互，随后收到真实推送；完整 React 购物车也已接入 | `01` 至 `05` 的 PNG 截图、`browser-verification.json` |

表中 Java 路径相对于 `backend/src/main/java/com/bookstore`。精简提交版直接编译这里的同一份源码。

## 启动

需要 Docker（已启动）、JDK 21、Maven 和 Python 3。完整 React 书店额外需要 Node.js/npm；精简提交包的演示页不需要 Node.js，不需要 MySQL，也不需要模型密钥。

```bash
cd BookStore_s4
export JAVA_HOME=/你的/JDK21/路径
bash scripts/run-assignment5.sh
# 或 bash start.sh
```

脚本启动 `compose.assignment5.yml` 中的 Apache 官方 Kafka 3.9.1 单节点 KRaft，执行测试并构建后端，最后启动服务。首次执行需要联网下载依赖。

- 演示页：<http://127.0.0.1:8080/orders-demo.html>
- 完整书店（仓库完整工程）：<http://127.0.0.1:5173>
- 顾客：`student` / `bookstore-demo-2026`
- 管理员：`admin` / `admin-demo-2026`

Kafka 只向本机开放 9092 端口，Topic 有 3 个分区、1 个副本。`Ctrl+C` 停止本次启动的 Java/React 进程；Kafka 保持运行。停止本作业 Kafka：

```bash
docker compose -f compose.assignment5.yml stop kafka
```

只运行演示页，可用 `WITH_FRONTEND=0 bash scripts/run-assignment5.sh`。程序使用 `demo,assignment4,assignment5` 三个 profile：前两个复用隔离的 H2 演示数据与 JWT 账号，最后一个开启 Kafka 订单与 SSE。旧作业 profile 保留原来的同步接口，作业 5 启动入口默认启用异步接口。

临时 JWT 密钥由脚本随机生成，不写入源码或提交包。H2 数据在后端停止后清空，重启后需要重新登录。旧 Kafka 消息如果在新数据库中找不到对应排队记录，会被忽略，不会意外重复扣库存。

## 操作与实测

打开演示页，依次点击“提交成功示例”和“提交失败示例”。排队期间点击“页面仍可交互”，可以看到计数增加。两个卡片展示实际请求 JSON、202 响应、SSE 内容及耗时。

本机一次实测（真实浏览器、真实单节点 Kafka）：

| 场景 | POST 返回 202 | 最终 SSE 到达 | 结果 |
| --- | ---: | ---: | --- |
| 购买 1 本 | 10 ms | 3027 ms | `SUCCEEDED`，生成订单并扣减 1 本库存 |
| 购买 1000000 本 | 15 ms | 3024 ms | `FAILED`，提示库存不足，没有生成订单 |

这些数据来自 `evidence-kafka/browser-verification.json`，耗时受机器与首次连接开销影响，不是性能保证。证据内时间戳采用 UTC，日志采用本机时区。

截图：

1. `01-accepted-and-responsive.png`：202 已返回，最终结果尚未到达，页面交互计数为 1。
2. `02-success-push.png`：成功 SSE 的订单号与完成时间。
3. `03-success-and-failure.png`：成功、失败两条完整请求及推送流程。
4. `04-bookstore-queued.png`：原 React 购物车显示排队号。
5. `05-bookstore-success.png`：收到 SSE 后显示订单号并清空已购买商品。

## 接口与消息

先获取 JWT，随后在 `Authorization` 头传入 Bearer Token。`queueId` 是 UUID 排队任务号；成功后的 `orderId` 才是原书店的数字订单号，两者用途不同。

```bash
TOKEN=$(curl -fsS http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"student","password":"bookstore-demo-2026"}' \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["accessToken"])')

curl -i http://127.0.0.1:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"items":[{"bookId":1,"quantity":1}]}'

# 将响应中的 queueId 填在下面；-N 关闭 curl 输出缓冲。
QUEUE_ID=填入返回的排队号
curl -N "http://127.0.0.1:8080/api/orders/$QUEUE_ID/stream" \
  -H "Authorization: Bearer $TOKEN"
```

202 响应包含 `queueId`、`status: QUEUED`、说明与创建时间，并附带查询快照的 `Location` 头。Kafka JSON 结构为：

```json
{
  "queueId": "由服务端生成的 UUID",
  "userId": 1,
  "order": {"items": [{"bookId": 1, "quantity": 1}]}
}
```

`userId` 从已验证的登录身份取得。消费者按 `queueId` 加锁读取已保存的原始请求，不能通过改写消息里的用户或订单项更改已受理的任务。

SSE 示例（实际 UUID 和时间见证据）：

```text
event:order-status
id:<queueId>:SUCCEEDED
retry:1000
data:{"queueId":"...","status":"SUCCEEDED","message":"库存扣减完成，订单创建成功","orderId":2,"createdAt":"...","finishedAt":"..."}
```

可调用 `GET /api/orders/{queueId}/status` 获取任务快照。请求字段非法时返回 400，未登录为 401，访问他人任务为 403，任务不存在为 404。Kafka 发送失败时返回 503，不会把未确认的投递当成成功受理。若生产确认丢失但消费者已经完成，则保留已完成结果并返回 202，避免误导用户重复提交。

## 事务与 SSE 行为

`OrderPlacementService` 复用书店原有的真实库存扣减逻辑。相同图书的多行购买数量先合并，所有图书按 ID 顺序加悲观锁，再检查库存。库存、订单和任务终态处于同一数据库事务中；失败回滚后，另一个事务记录失败结果。消费者不依赖 HTTP 请求线程的登录上下文。

任务表按 `queueId` 加锁，已完成任务直接返回原结果。这样即使 Kafka 重投消息，也不会再次扣减库存。业务校验失败直接记录失败；临时异常由 `DefaultErrorHandler` 间隔 1 秒重试 2 次，耗尽后记录并推送失败；如果连失败记录都无法持久化，则恢复逻辑继续抛错，不会伪装成成功。

SSE 订阅注册和结果发布共享一个同步区，避免“处理完成恰好发生在订阅期间”造成漏通知。新订阅先收到数据库当前快照，因此晚连接与断线后重连仍能拿到终态。每 15 秒发心跳，120 秒连接超时；完成、超时和异常都会清理连接。

前端用 `fetch` 读取 SSE 流，以便携带 JWT Header。网络中断后会重新订阅同一排队号；已受理的请求不会因为 SSE 断开而再次 POST。只有收到成功终态才显示成功弹窗和移除已购买商品，业务失败保留购物车。

## 验证与复现

完整工程：

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
```

精简提交包：

```bash
mvn -f assignment5/pom.xml test
```

已启动服务后：

```bash
python3 scripts/verify_assignment5.py
# 截图工具需在本机可解析 playwright，并已安装其 Chromium。
node scripts/capture_assignment5.cjs
# 以下需完整书店前端启动在 5173。
node scripts/capture_bookstore_assignment5.cjs
```

完整后端 **94 项**、前端 **27 项**、精简提交版 **44 项**测试通过；外部 HTTP/SSE **18 项**检查通过，前端 lint 和构建通过，浏览器完成演示页及真实购物车验证。

检查包含：快速 202、库存真正提交、整单库存不足回滚、重复消息幂等、最后一本图书的竞争、SSE 终态重放、归属权限、非法输入、Broker 发送失败、分段 SSE 解析、断线后复用同一任务。测试统计与逐项结果保存在 `evidence-kafka/test-summary.json` 和其他 JSON 证据文件中。

## 提交包内容与边界

提交包只收录订单队列及其必要的书店认证、库存、订单源码，精简 Maven 配置，前端修改文件，脚本、说明和本次证据。不包含整套 React/AI/RAG 工程、`node_modules`、JAR、Kafka 镜像、JDK、运行密钥或 `.env`。解压后可以独立运行演示页；`frontend` 中的修改文件用于覆盖原工程，不是完整 React 分发包。`MANIFEST.sha256` 可校验提交文件。

本作业按单后端实例实现 SSE 连接管理，Kafka 单节点用于本地演示。H2 使用内存存储，数据库与 Kafka 发送没有实现分布式事务或 outbox，因此进程在“保存任务、发送 Kafka”两步之间崩溃时，不能保证恢复投递。它没有承诺跨数据库重置的 exactly-once，也不是多后端实例的推送架构。

## 参考资料

- [Apache Kafka 3.9 Quick Start](https://kafka.apache.org/39/getting-started/quickstart/)：单节点 Kafka/KRaft 运行方式。
- [Spring Kafka Sending Messages](https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html)：`KafkaTemplate` 与发送确认。
- [Spring MVC Asynchronous Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)：`SseEmitter` 与异步响应生命周期。
