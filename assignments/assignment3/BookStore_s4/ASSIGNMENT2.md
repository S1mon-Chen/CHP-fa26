# 作业 2 实现与验证说明

学号：524031910668　姓名：陈熹朗

## 1 实现结果

新增 `POST /api/ai/tool-chat`，接收用户问题及最近的文本对话。模型请求带有两个 Function Schema，后端只执行返回的 `tool_calls` 中列出的工具，并将带 `tool_call_id` 的工具结果追加到同一消息上下文。整个过程循环到模型生成自然语言回答，最多请求模型五次。原有 `/api/ai/chat` 接口保留，现有前端聊天窗口改用新接口。

本次实际使用用户指定的 `https://api.deepseek.com` 和 `deepseek-flash`。密钥通过服务端环境变量读取，前端不接触密钥，提交包不包含 `.env`。

## 2 五项评分标准对应关系

| 评分项 | 实现位置 | 可核查行为 |
| --- | --- | --- |
| Function Schema | `backend/src/main/resources/assistant/tools.json` | 两个函数均有名称、描述、object 参数、string 类型的 isbn、required 和 additionalProperties=false |
| 解析 Tool Call | `ToolChatService.java`、`ModelClient.java` | 解析 choices[0].message.tool_calls；校验 id、type、name、arguments；支持同一响应中多个工具调用 |
| 本地函数执行 | `BookTools.java`、`scripts/mock_competitor.py` | check_inventory 读取数据库；get_competitor_price 调用独立的模拟 HTTP 服务 |
| 结果回传与回答 | `ToolChatService.java`、`AIAssistant.jsx` | 保存 assistant 原始 tool_calls；为每个调用写入 role=tool、tool_call_id、JSON 字符串 content；前端显示 answer |
| 异常与自我纠正 | `BookTools.java`、`ToolCallingTest.java` | 错误 ISBN、非法 JSON、未知函数、竞品超时均有结构化错误；模型可根据错误修正参数或重试 |

上表 Java 文件位于 `backend/src/main/java/com/bookstore/assistant/`，测试位于 `backend/src/test/java/com/bookstore/assistant/`，组件位于 `frontend/src/components/`。

## 3 调用流程

1. 用户在 React 聊天窗口输入问题。前端沿用项目的 session/CSRF 客户端，发送 query 和最近 12 条 user/assistant 文本。
2. 后端加入 System Prompt，设置 `model`、`messages`、`tools`、`tool_choice=auto`、`stream=false`，POST 到配置的 base URL 加 `/chat/completions`。
3. 模型返回 `assistant.tool_calls`。后端打印函数名称与原始参数，解析 arguments 的 JSON 字符串，通过固定白名单分派到 Java 方法，不用关键词匹配代替模型决策。
4. 工具执行成功或失败都生成 JSON。保留原始 assistant 消息（包括服务商返回的推理字段），随后按每个调用的 id 追加 `role=tool` 消息。
5. 再次请求模型。模型可以修正参数、重试或直接回答；前端只把最终 `answer` 作为回答展示，查询明细可折叠展开。

消息顺序示例：

```json
[
  {"role":"system","content":"你是电子书店的库存和比价助手……"},
  {"role":"user","content":"查 ISBN 12345 的库存和竞品价格"},
  {"role":"assistant","content":null,"tool_calls":[
    {"id":"call_inventory","type":"function","function":{"name":"check_inventory","arguments":"{\"isbn\":\"12345\"}"}},
    {"id":"call_price","type":"function","function":{"name":"get_competitor_price","arguments":"{\"isbn\":\"12345\"}"}}
  ]},
  {"role":"tool","tool_call_id":"call_inventory","content":"{\"ok\":true,\"stock\":12}"},
  {"role":"tool","tool_call_id":"call_price","content":"{\"ok\":true,\"price\":45.5,\"simulated\":true}"}
]
```

以上是便于阅读的协议节选；完整真实调用结果保存在 `evidence/live-success.json`。

## 4 工具与数据

`check_inventory(isbn)` 每次通过 `BookRepository.findFirstByIsbnOrderByIdAsc` 查询当前数据库，返回书名、库存、本店价格、币种和查询时间。没有缓存库存，也不从聊天历史推断数量。数据库查无此书返回 BOOK_NOT_FOUND，售罄返回成功且 stock=0，二者语义不同。既有实体没有 ISBN 唯一约束，因此在重复记录时采用 id 最小的一条。

`get_competitor_price(isbn)` 使用 Java HttpClient GET 请求配置好的模拟接口，连接超时为 2 秒，请求超时默认 1500 毫秒。只接受匹配 ISBN、非负数值价格和 CNY 币种的结果；只复制已知字段。接口地址来自服务端配置，模型无法指定任意 URL。

模拟竞品服务使用 Python 标准库，在 127.0.0.1:8091 监听；不查询真实商家。默认示例数据如下。本店数据写入 H2 数据库，竞品数据位于 Python 字典，两者独立。

| ISBN | 图书 | 本店库存 | 本店价格 | 模拟竞品价格 |
| --- | --- | ---: | ---: | ---: |
| 12345 | Function Calling 入门（虚构教学书） | 12 | 49.90 元 | 45.50 元 |
| 9787111421900 | 深入理解 Java 虚拟机 | 8 | 79.00 元 | 72.00 元 |
| 9780134685991 | Effective Java | 0 | 99.00 元 | 89.00 元 |

所有库存及价格都是教学数据，不代表真实销售情况。12345 是题目示例的特例；其他书号按 ISBN-10 或 ISBN-13 及校验位验证。

## 5 异常处理与 Self Correction

错误统一采用如下格式，作为工具结果回传模型，而不是终止整个对话：

```json
{"ok":false,"error":{"code":"COMPETITOR_TIMEOUT","message":"模拟竞品接口超时，可以重试一次；仍失败则说明暂无报价。","retryable":true}}
```

对于 `978-7-111-42190-0`，首次调用返回 INVALID_ISBN，并附 `suggested_isbn=9787111421900`。只有删除空格/连字符或大写末尾 x 后能通过校验，才会提供确定的修正建议。真正缺位或校验位错误的书号不会被悄悄改成另一本书；模型应向用户确认。

模拟服务的 `--timeout-once` 让每个 ISBN 的第一次请求延迟 3 秒，而第二次立即响应。由于它使用 ThreadingHTTPServer，第一次延迟不会阻塞重试。Java 将实际 HttpTimeoutException 转为 COMPETITOR_TIMEOUT，由模型决定是否再发起一次调用。

执行器最多实际执行相同函数和 ISBN 两次。继续调用会收到 RETRY_LIMIT；整段处理最多五轮，每轮最多八个 Tool Call，避免无限循环。模型接口鉴权错误、不可连接、超时、截断或协议错误返回清晰的 HTTP 错误；不会把密钥或上游原始错误正文暴露给前端。

## 6 配置和运行

在项目根目录创建 `.env`（等号两边不加空格）：

```dotenv
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-flash
LLM_API_KEY=替换为自己的密钥
LLM_TIMEOUT_SECONDS=90
COMPETITOR_TIMEOUT_MS=1500
COMPETITOR_TIMEOUT_ONCE=0
```

启动：`bash scripts/run-assignment2.sh`。等待 Ready 后打开 `http://127.0.0.1:5173`。脚本负责 Maven 构建、按需 npm ci、启动 Spring Boot、Vite 和模拟竞品服务。日志在 `logs/`，函数调用记录位于 `logs/backend.log`。端口被其他服务占用时脚本会退出，不会终止别的项目。

无浏览器检查：

```bash
python3 scripts/smoke_chat.py --output evidence/live-success.json
```

演示超时：停止演示后，将 `.env` 中 `COMPETITOR_TIMEOUT_ONCE` 改为 1，再次启动并查询 12345 的库存和竞品价格。每次重启模拟服务会清空“已延迟”记录。

演示格式纠正：输入“请先按原样调用 check_inventory 查询 ISBN 978-7-111-42190-0；如果工具报格式错误，再按工具建议修正。”普通查询时模型也可能在调用前主动规范化书号，这也是合法行为，但不能据此声称经过了错误反馈。

如需读取已有 MySQL 库存，单独使用 `assistant` profile，并按原工程配置数据库；此时不启用 `demo`，也不会执行示例数据 SQL。对应 ISBN 必须存在于原数据库。前端与模拟竞品服务仍按同样方式运行。

## 7 测试

```bash
cd backend && mvn test
cd ../frontend && npm test && npm run lint && npm run build
```

已执行结果：后端 35 项测试通过，前端 13 项测试通过，ESLint 和 Vite production build 通过。构建保留原项目较大的 bundle 警告，不影响构建成功。

新增后端测试包括两个层次：ToolCallingTest 使用脚本化的本地 HTTP 服务模拟模型协议，覆盖两个工具同轮调用、关联 id、推理字段保留、ISBN 修正、非法 JSON 修正、真实 HTTP 超时及重试、重试上限、未知函数、缺失/重复 id、无工具回答和上游错误隐藏；ToolChatIntegrationTest 启动 Spring/H2/MockMvc，验证数据库库存变化可见、匿名用户携带 CSRF 可以访问、缺少 CSRF 被拒绝、空问题被校验。

脚本化的协议测试不等同于真实模型测试。真实 DeepSeek 调用另存于 `evidence/`，其中 live-success.json 记录模型一次返回两个 Tool Call，第二次模型请求返回最终回答；查询记录包含库存 12、本店价格 49.90 和模拟竞品价格 45.50。

2026 年 10 月 22 日的三组真实 API 验证如下。以下结果来自实际保存的请求/响应记录，不是手工编写的模型输出。

| 记录 | 观察到的流程 | 模型请求次数 |
| --- | --- | ---: |
| `evidence/live-success.json` | 同轮调用库存和竞品价格，回传两个结果后生成回答 | 2 |
| `evidence/live-timeout-recovery.json` | 首轮库存成功、竞品超时；第二轮模型重试竞品并成功；第三轮生成回答 | 3 |
| `evidence/live-isbn-correction.json` | 先传带连字符的 ISBN，收到 INVALID_ISBN 和建议书号；第二轮改用正确格式；第三轮生成回答 | 3 |

`evidence/browser-chat.png` 是浏览器中真实提交问题并收到模型回答后的截图；已检查折叠查询记录能显示两个函数的参数和结果。`evidence/test-results.txt` 汇总自动化检查结果。

## 8 参考与边界

兼容端点的 tools、tool_choice、tool_calls 与消息参数参考 [Ollama 官方兼容接口文档](https://docs.ollama.com/api/openai-compatibility)，实际服务配置以本次 DeepSeek 实测为准。没有使用模型 SDK，HTTP 和 JSON 解析使用 Java 17 HttpClient 与原项目已有 Jackson。

此提交演示教学功能。模型必须支持标准工具调用；提供商不可用时，系统显示失败提示，不伪造成功回答。对话历史只保留当前浏览器页面内的最近 12 条文本，刷新后清空；工具状态仅存在当前后端请求中。
