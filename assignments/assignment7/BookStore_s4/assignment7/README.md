# 作业 7 图书详情缓存与接口限流

学号：524031910668　姓名：陈熹朗

本模块提供 `GET /api/books/{id}` 图书详情接口，使用真实 MySQL、Redis Cache-Aside、空对象缓存防穿透和 Sentinel 5 QPS 限流。沿用原书店详情接口路径，独立数据库与端口便于复现。本次提交以本模块为运行入口，不需要启动复制目录里的旧后端、React、Kafka 或 Seata。

## 评分项对应

| 评分项 | 实现及证据 |
| --- | --- |
| Redis 读写缓存分离 | `BookCacheService.find` 先读缓存，未命中查库回填；`save` 在数据库提交后 DEL |
| 防穿透或防击穿 | 选择防穿透：不存在的 ID 缓存 `__NULL__`，TTL 30 秒，命中后直接 404 |
| 引入治理组件 | POM 引入真实 `com.alibaba.csp:sentinel-core:1.8.9`，使用 `SphU.entry` |
| 限流规则 | `BookFlowControl` 注册统一资源 `GET:/api/books/{id}`，QPS 5，直接拒绝 |
| 压测与截图 | `scripts/loadtest.py` 50 线程同步突发；浏览器内置并发工具；`evidence/01-http429-loadtest.png` |

## 架构和实现

```text
GET /api/books/{id}
  -> Sentinel 统一资源 5 QPS
       超限 -> HTTP 429 + Retry-After: 1
       放行 -> Redis GET a7:book:{id}
                 JSON       -> HTTP 200，X-Cache: HIT
                 __NULL__   -> HTTP 404，X-Cache: NULL_HIT
                 未命中     -> MySQL SELECT
                                  有数据 -> Redis SET JSON，TTL 120～150 秒
                                  无数据 -> Redis SET __NULL__，TTL 30 秒

PUT /api/books/{id}
  -> 参数校验 -> MySQL 事务 INSERT/UPDATE -> COMMIT -> Redis DEL
```

Redis 使用 `StringRedisTemplate` 与 JSON 字符串，不依赖 Java 对象序列化。`__NULL__` 是实际存储的标记，与 Redis GET 返回 null（键不存在）不同；负缓存不返回 HTTP 200 或伪造图书。正常 TTL 加入 0～30 秒随机抖动，降低大量键同时到期的概率。缓存 JSON 损坏时删除并回源。

`TransactionTemplate.execute()` 返回时数据库事务已提交，之后再删缓存。新增图书也删除原来的空对象，使新数据立即可见。数据库失败时不会执行缓存删除；数据库成功但 Redis 删除失败时返回 HTTP 503，明确带 `databaseCommitted: true`，避免把已提交写入报告成未提交。

Sentinel 配置固定加载于启动阶段，无需 Dashboard 才能生效。所有图书 ID 共用一个资源，不按 ID 或 IP 各发 5 个额度。`FlowRule` 设置 `FLOW_GRADE_QPS`、`count=5`、`STRATEGY_DIRECT` 和 `CONTROL_BEHAVIOR_DEFAULT`。Controller 在访问缓存之前调用 `SphU.entry`，被拒绝时捕获 `BlockException` 并返回 429；try-with-resources 确保所有放行路径都退出 Entry。入口仅对 Sentinel 准入检查做短暂同步，避免同一 JVM 的并发检查与计数竞争；缓存及数据库处理仍可并发执行。

## 环境与启动

需要 JDK 17 或更高版本、Maven 3.9+、Docker Compose v2、Python 3。已使用 JDK 20.0.2 验证，编译目标为 Java 17。Spring Boot 3.5.16、Sentinel 1.8.9；Docker 镜像为 `mysql:8.4` 和 `redis:7-alpine`。本次实际版本与镜像信息记录在 `evidence/environment.json`。

```bash
# 从提交包中的 BookStore_s4 目录运行
bash start.sh

# 也可进入 BookStore_s4/assignment7 后执行
bash scripts/run.sh
```

打开 <http://127.0.0.1:18087/>，点击“发起 50 个并发请求”。脚本在启动时编译应用，等待两个容器健康，并显式开启本地观测接口。JDK 的选择通过 `JAVA_HOME` 控制；首次运行需要下载 Maven 依赖。Ctrl+C 停止本次 Java 进程；在本模块内运行 `docker compose stop` 停止数据库和 Redis，MySQL 数据卷保留。

| 组件 | 端口 | 数据与作用 |
| --- | ---: | --- |
| Spring Boot | 18087 | 图书 API、压测页面与本地观测接口 |
| MySQL | 13307 | 专用 `bookstore_cache.books` 数据表 |
| Redis | 16379 | `a7:book:` 前缀，128 MB，allkeys-lru |

服务及容器发布端口都绑定 127.0.0.1。Compose 项目名固定为 `bookstore-assignment7`，不操作其他作业的数据。公开密码仅用于这个本地实验库。`application.properties` 默认关闭 `/demo/*`，演示启动脚本打开；`/demo/stats` 展示本 JVM 业务计数与实际生效规则，`/demo/health` 检查真实依赖。

## 接口示例

```bash
curl -i http://127.0.0.1:18087/api/books/1
curl -i http://127.0.0.1:18087/api/books/999999
curl -i -X PUT http://127.0.0.1:18087/api/books/10001 \
  -H 'Content-Type: application/json' \
  -d '{"title":"缓存实验","author":"测试","price":42.50,"stock":10}'
```

详情成功返回 200，缺失返回 404，非法参数返回 400，限流返回 429，依赖不可用返回 503。`X-Cache` 的值为 `MISS`、`HIT`、`NULL_MISS`、`NULL_HIT`。429 的示例响应为：

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 1
X-RateLimit-Resource: GET:/api/books/{id}
Content-Type: application/json

{"code":"TOO_MANY_REQUESTS","message":"请求过于频繁，请稍后重试","resource":"GET:/api/books/{id}"}
```

## 验收与压测

```bash
cd BookStore_s4/assignment7
python3 scripts/verify.py --outage
python3 scripts/loadtest.py
```

`verify.py` 使用 HTTP、Redis CLI 与 MySQL SQL 实测，没有 Mock。它用毫秒时间戳创建自己的验收图书，在 finally 中清理；只清除图书 1 的缓存，不改变已有业务图书。`--outage` 会短暂停止本作业的 Redis，随后恢复；不传该参数则跳过两项故障测试。验证期间请不要同时操作页面或另起压测，否则会消耗同一个全局额度。脚本若出现异常会返回非零，并将结果标记为未完成。

本次 28 项检查通过，包括缓存命中只查一次 SQL、负缓存命中不查库、负缓存 TTL 与到期重建、新增清除负缓存、更新后的数据可见性、损坏缓存恢复、参数校验、限流、跨 ID 共享额度、Redis 故障与恢复。故障时接口选择返回 503，避免缓存不可用时所有请求直接冲击数据库。

50 个线程通过 Barrier 同时发起请求，空闲后短突发实测 5 个 200、45 个 429，缓存命中增加 5，SQL 查询增加 0。计数覆盖整个接口：被拒绝的 45 个请求在访问缓存之前已被拦截。第二组交替访问图书 1、2、3 的测试仍只放行 5 个请求。完整逐请求状态、耗时、响应头、响应体和服务端计数在以下证据中：

- `evidence/verification.json`、`verification.txt`：验收断言及观察数据。
- `evidence/loadtest.json`：Python 50 线程压测原始响应。
- `evidence/browser-loadtest.json`：浏览器独立执行的 50 请求压测。
- `evidence/01-http429-loadtest.png`：实际浏览器截图，包含 429 响应原文。
- `evidence/build.log`、`submission-build.log`：源码及精简提交包独立构建日志。

浏览器截图脚本需要 Playwright 和 Chromium，属于可选工具：

```bash
node scripts/capture.cjs
```

可通过 `PLAYWRIGHT_CHROMIUM_EXECUTABLE` 指定 Chromium 可执行文件路径。浏览器会同时调度 50 次 fetch，底层连接池可能分批发送；独立 Python 压测使用 50 个线程同步释放，作为并发验证的主要数据。

另附 `scripts/books-50-users.jmx`，可在 JMeter 中打开，或执行：

```bash
jmeter -n -t scripts/books-50-users.jmx -l evidence/jmeter-results.jtl
```

JMeter 自带统计会把 429 标记为 error，此处是预期限流结果，应查看响应码和响应体。本次证据来自已执行的 Python 与浏览器工具，未将 JMeter 计划描述为已执行测试。

## 实现边界

本题“防穿透或防击穿”选择防穿透，未实现分布式锁。负缓存主要吸收同一缺失 ID 的重复请求；不断更换不存在的 ID 仍可能回源，但会先受到全接口 5 QPS 约束。冷缓存并发未命中时仍可能有多个请求查库，不能将这一实现称为单线程缓存重建。

Cache-Aside 提供最终一致性。并发读回填与写后删除存在竞态，进程也可能在数据库提交后、删除缓存前退出，因而不能保证强一致；正常 TTL 将此类旧值的保留时间限制在最多约 150 秒。直接 SQL 写入绕过服务不会触发主动失效。本作业不声明实现主从分离或 Redis/MySQL 原子双写。

Sentinel 本地规则作用于单 JVM，多个实例的总额度不是 5 QPS；集群限流需要额外的集群规则或统一网关。滑动统计窗口和客户端调度使长时间请求的统计与单次短突发不同，不能把每次 50 请求都必然 5/45 当成保证。当前 PUT 是本地演示写入接口，未添加账户鉴权，也不在详情 GET 的限流资源内。

## 提交包

```bash
python3 scripts/package.py
```

采用允许清单收集本次 Java/POM、配置、SQL、HTML、脚本、README、报告和验证证据，附 SHA-256 清单。排除复制来的旧工程、target、JAR、class、node_modules、.env、模型文件、日志运行目录和数据库数据。解压后的精简工程可独立构建运行。

已将精简提交包解压到独立临时目录，使用 JDK 17 执行 `mvn -B clean package`，结果为 BUILD SUCCESS。独立构建日志为 `evidence/submission-build.log`；文件清单和 SHA-256 校验也已通过。

## 参考资料

- [Redis Cache-Aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/)：读未命中回源与写后失效。
- [Spring Data Redis 模板接口](https://docs.spring.io/spring-data/redis/reference/redis/template.html)：RedisTemplate 与 StringRedisTemplate。
- [Sentinel 资源与规则](https://sentinelguard.io/en-us/docs/basic-api-resource-rule.html)：SphU Entry 与 FlowRuleManager。
- [Sentinel 流量控制](https://sentinelguard.io/en-us/docs/flow-control.html)：QPS 阈值与直接拒绝行为。
