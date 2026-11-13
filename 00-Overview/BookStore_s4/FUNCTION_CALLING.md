# 作业 2：客服 AI 助手的 Function Calling

用户在前端问：“帮我查一下 ISBN为12345 的书还有多少本，顺便看看别家卖多少钱”。DeepSeek 大模型会触发两个函数调用，后端拦截调用、打印函数名和参数，在本地模拟执行，再把结果回传给模型，最后把模型生成的自然语言回答显示在前端。

- 模型：DeepSeek-V4.1-Flash（API 模型 ID `deepseek-flash`），使用 OpenAI 兼容的 `/chat/completions` 接口
- API Key：从环境变量 `LLM_API_KEY` 读取，不写入代码和配置文件

## 1. 文件清单（本次新增或修改）

| 文件 | 说明 |
|---|---|
| `backend/.../functioncalling/ToolDefinitions.java` | **Function Schema**：两个函数的名称、描述、参数 JSON Schema |
| `backend/.../functioncalling/BookstoreTools.java` | 两个 Skill 的本地实现：`check_inventory`、`get_competitor_price`；参数校验和结构化错误 |
| `backend/.../functioncalling/ToolCallContext.java` | 单次对话内记录调用次数（用于模拟“首次超时、重试成功”） |
| `backend/.../functioncalling/FunctionCallingService.java` | **主流程**：请求模型 → 解析 `tool_calls` → 执行 → 回传 `role=tool` → 循环到最终回答 |
| `backend/.../functioncalling/LlmClient.java` | 调用 DeepSeek 接口，处理超时、HTTP 错误、响应格式错误 |
| `backend/.../functioncalling/FunctionCallingDTOs.java` | 请求和响应数据结构 |
| `backend/.../controller/FunctionCallingController.java` | `POST /api/ai/function-chat`、`GET /api/ai/tools` |
| `backend/.../repository/BookRepository.java` | 新增 `findFirstByIsbn` |
| `backend/src/main/resources/application.properties` | 数据库改为 `bookstore_s4`；新增 `llm.*`、`bookstore.competitor.*` 配置（含数据库密码，不放进提交包；配置项见下方） |
| `backend/src/main/resources/function-calling-seed.sql` | 给演示书籍补充 ISBN |
| `backend/src/test/.../BookstoreToolsTest.java` | 本地函数单元测试（9 个） |
| `backend/src/test/.../FunctionCallingLiveTest.java` | 真实调用 DeepSeek 的端到端测试（3 个场景） |
| `frontend/src/pages/FunctionCallingPage.jsx` / `.css` | 智能客服对话界面，展示每次函数调用的参数和结果 |
| `frontend/src/services/aiService.js` | 新增 `functionChat` |
| `frontend/src/App.jsx`、`components/Header.jsx` | 新增“智能客服”菜单 |

新增的配置项：

```properties
llm.base-url=https://api.deepseek.com
llm.model=deepseek-flash
llm.timeout-seconds=60
bookstore.competitor.timeout-ms=2000
bookstore.competitor.slow-delay-ms=3000
```

## 2. 调用流程

```
前端 FunctionCallingPage
   │  POST /api/ai/function-chat { query, history }
   ▼
FunctionCallingService
   │ ① messages = [system, ...history, user]，携带 tools 请求 DeepSeek
   │ ② 响应里有 tool_calls？
   │      是 → 打印 “拦截到模型调用请求 -> 函数名(参数)”
   │           BookstoreTools.execute(name, arguments)   ← 本地模拟执行
   │           追加 assistant(tool_calls) 消息 + role=tool 消息（tool_call_id 对应）
   │           回到 ①（最多 5 轮，超过后 tool_choice=none 强制作答）
   │      否 → content 就是最终回答
   ▼
{ reply, toolCalls:[{round, name, arguments, result, success, durationMs}], rounds }
```

## 3. 对照评分标准

### 3.1 Function Schema（`ToolDefinitions.java`）

每个函数都有 `name`、`description`（说明什么时候调用），`parameters` 是 JSON Schema：`isbn` 为 `string` 类型，带格式说明，列入 `required`，并设置 `additionalProperties: false`。运行后可以通过 `GET /api/ai/tools` 查看。

### 3.2 解析 Tool Call（`FunctionCallingService.chat`）

读取 `choices[0].message.tool_calls[]`，取出 `id`、`function.name`，以及 `function.arguments`（模型返回的是 JSON **字符串**，需要再解析一次）。模型可能在同一轮**并行**返回多个调用，会逐个执行。控制台输出示例：

```
[Function Calling] 第 1 轮：拦截到模型调用请求 -> check_inventory({"isbn": "12345"})  tool_call_id=call_00_fQNG...
[Function Calling] 第 1 轮：拦截到模型调用请求 -> get_competitor_price({"isbn": "12345"})  tool_call_id=call_01_3u5m...
```

### 3.3 本地函数模拟调用（`BookstoreTools.java`）

- `check_inventory`：通过 `BookRepository.findFirstByIsbn` 查询**真实库存**，返回书名、本店价格、库存数量和库存状态（有货 / 库存紧张 / 缺货）
- `get_competitor_price`：模拟外部比价 API。在 `CompletableFuture` 中执行并设置 2 秒超时。报价根据 ISBN 计算，同一本书每次结果一致，在本店价格的 80%~110% 之间浮动（京东 / 当当 / 淘宝）

### 3.4 回传结果并生成最终回答

把带 `tool_calls` 的 assistant 消息**原样**放回 `messages`，再为每个调用追加 `{"role":"tool","tool_call_id":..., "content": 结果JSON}`，然后再次请求模型。不断循环，直到模型不再请求调用函数。

### 3.5 异常处理与 Self-Correction

函数**不抛异常**，而是返回结构化错误 `{success:false, error, message, hint, retryable}`。system prompt 告诉模型如何根据错误自我纠正：

| 场景 | 函数返回 | 模型行为 |
|---|---|---|
| ISBN 格式错误，如 `12a45` | `INVALID_ISBN` | 请用户核对 ISBN，不编造（前后端联调已验证） |
| ISBN 输错，如 `12354` | `BOOK_NOT_FOUND` + `similar_isbns:["12345"]` | **自动改用 12345 重试**，并在回答里说明“您输入的 12354 没找到，我按 12345 查询了，请确认”（端到端测试已验证） |
| 比价接口超时（ISBN 以 `999` 开头时，首次调用延迟 3 秒） | `UPSTREAM_TIMEOUT`, `retryable:true` | **用相同参数重试**，第二次成功（端到端测试已验证） |
| 缺少参数、参数不是合法 JSON、函数名不存在 | `MISSING_ARGUMENT` / `INVALID_ARGUMENTS` / `UNKNOWN_FUNCTION` | 向用户追问或改正参数 |
| 大模型接口超时或报错、未配置 Key | `LlmException` | 返回友好的错误提示，前端用红框显示 |
| 模型一直调用函数 | 超过 5 轮 | 用 `tool_choice=none` 强制模型作答 |

## 4. 运行方法

```bash
# 1. 准备数据（MySQL 已启动）。本项目使用独立数据库 bookstore_s4，
#    init.sql 中写死的是 bookstore，先替换库名再执行，避免误删其他项目的表
sed -E 's/(DATABASE IF NOT EXISTS|USE) bookstore([; ])/\1 bookstore_s4\2/' \
    backend/src/main/resources/init.sql > /tmp/init_s4.sql
grep -n bookstore /tmp/init_s4.sql   # 确认库名只出现 bookstore_s4
mysql -uroot -p < /tmp/init_s4.sql
mysql -uroot -p bookstore_s4 < backend/src/main/resources/function-calling-seed.sql

# 2. 启动后端（终端里需要能读到 LLM_API_KEY）
cd backend && mvn spring-boot:run

# 3. 启动前端，打开 http://localhost:5173 → 顶部菜单“智能客服”
cd frontend && npm install && npm run dev
```

演示数据：`12345`（深入理解计算机系统，库存 100）、`99901`（算法导论，演示超时重试）、`9787111075752`（设计模式，库存 3，库存紧张）。

## 5. 测试

```bash
cd backend
mvn test -Dtest='BookstoreToolsTest,FunctionCallingLiveTest'
```

- `BookstoreToolsTest`：Schema 结构、库存查询、ISBN 规范化、各类错误、超时后重试
- `FunctionCallingLiveTest`：真实调用 DeepSeek（数据库用 Mock 代替），验证作业示例、ISBN 笔误自我纠正、超时重试三个场景

实测结果：12 个测试全部通过。作业示例中，模型在第 1 轮**并行**调用两个函数，第 2 轮给出最终回答。
