# 第二轮：受限检索 Agent Harness

模型负责决定下一步，第一轮工具负责真实资料检索，Harness 负责权限、预算、证据和结束条件。沿用原 Snapshot、search 和 ToolService，没有第二套资料层。

## 文件与阶段

- `agent_types.py`：严格 AgentConfig、Claim、Answer 与终止信号。
- `ollama.py`：标准库 HTTP 适配，非流式原生工具调用，不安装 Ollama SDK。
- `agent.py`：显式 INIT → DECIDE → VALIDATE_TOOL → EXECUTE_TOOL → OBSERVE → DECIDE；无工具调用进入 FINALIZE → VALIDATE_ANSWER。
- `tools.py`：新增共享 validate_call，invoke_tool 仍再次校验。Harness 不能通过校验成功的假设绕过统一入口。
- `scripted.py`：预设模型动作模板，用真实搜索返回的 ID 发起读取，绝不伪造工具观察。
- `recording.py`：带哈希引用的日志、大文本产物、严格完整性回放。
- `agent_cli.py`：新命令；原 CLI 命令仍保留。

资料正文始终是 role=tool 中的数据，不提升为 system。模型不能更改工具注册表、corpus 或允许访问集合。原资料工具有严格参数 Schema；引用 ID 和文件路径不互换。

## 已核对的 Ollama 协议

2026-10-12 核对 [官方工具调用](https://docs.ollama.com/capabilities/tool-calling)、[Chat API](https://docs.ollama.com/api/chat)、[结构化输出](https://docs.ollama.com/capabilities/structured-outputs)。采用 `/api/chat`、stream=false、tools 中的 function JSON Schema；观察按 role=tool、tool_name 返回。最终步骤去掉 tools 并提供 format=Answer JSON Schema。此版只支持明确的 `ollama-native-tools-v1`，没有自然语言猜测工具调用，也没有静默 JSON 模式回退。

本机 Ollama 0.35.0 实际返回 tool_calls 及调用 ID，模型为 qwen2.5:7b。若响应含 id，保留并通过 tool_call_id 配对；未返回 id 时 Harness 分配 run/round/index 本地 ID，写入工具观察 content 并保留 tool_name。原响应中的 function.index 同样保留。完整 assistant 工具消息后接每一条 tool 观察；串行处理一轮中的多调用。

INIT 先查已安装模型，再请求一次 list_documents 原生工具调用探针，仅校验其解析和参数，不执行工具。探针计入 model_requests 与总期限，不占决策轮和业务工具预算。模型不在线、未安装或不生成合法探针均明确 MODEL_ERROR，不下载、不切换 scripted。

## 预算、期限与无进展

默认 8 决策轮、10 工具尝试、每轮 3 调用、请求 60 秒、总期限 180 秒、传输重试 1 次、无进展阈值 2、答案修复最多 1 次、无证据返回检索最多 1 次。所有上限有 Pydantic 范围校验。

每条提议记录 tool_proposals；额度内的合法、非法、越权、单轮超限调用都扣 tool_attempts。若一轮包含超过剩余额度的调用，额度外提议不执行，另计 over_budget_rejections 并逐条给出拒绝观察；tool_attempts 不超过配置上限。已触发终止后，本轮剩余调用仍得到 RUN_STOPPING 观察，不被遗漏。下一轮不继续执行。

模型请求数独立统计，包括探针、DECIDE、FINALIZE、修复和传输重试。请求超时或连接/服务器错误有限重试，格式错误和多数 HTTP 客户端错误不重试。不存在无限退避或免费重试。

期限使用 monotonic 墙钟；每次模型/工具开始前检查，实际限时为 min(单次上限, 剩余期限)。Unix SIGALRM 在 CLI 主线程中中断阻塞操作，没有超时后仍继续工作的后台线程。此机制不是安全沙箱，只适用于本轮同步 macOS/Linux CLI；不支持直接放到 Web 工作线程。记录落盘与拒绝剩余提议的收尾可能有少量期限外开销。

重复指纹是规范化 JSON（对象键排序）的工具名、参数、观察哈希。列表顺序保留；没有偷偷修改搜索含义。新增发现（新的文档/片段）或新证据重置无进展计数；相同失败连续两次停止；读取第一次产生证据，随后两次重复读取无新增证据才停止。正常更换查询不因工具名称相同而停止，但仍受决策/工具预算限制。

终止状态：COMPLETED、BUDGET_EXHAUSTED、STALLED、MODEL_ERROR、TOOL_ERROR、ANSWER_VALIDATION_FAILED、DEADLINE_EXCEEDED。工具参数错误是模型可观察的拒绝，不自动当作工具基础设施崩溃。

## 证据与答案

只有成功 read_document 的完整片段能登记 E1、E2。登记前核对原 Snapshot 的所有片段字段与权限；相同快照/片段/内容哈希复用 ID。搜索摘要不登记。证据包含标题、行号、正文、ID、哈希与快照，并附到工具观察供模型引用。

最终生成是独立模型请求。每条 claim 必须有至少一项本次登记证据；引用必须属于当前快照。answered 要有结论且没有缺失信息；partial 要有结论并明确缺失信息；insufficient_evidence 的 claims 为空并列出缺口。无证据却尝试作答，反馈一次并回到 DECIDE；仍有错误最多修复一次，再失败即结束。

验证只检查结构、来源和引用存在性，不证明结论语义被原文支持。COMPLETED+insufficient_evidence 是正常业务结果。真实跨资料实测中的内容遗漏说明：即便程序完成，教学评测仍需逐条读证据。

## 日志与回放

每个 runs/<run_id> 保存 config.json、events.jsonl、model_interactions.jsonl、evidence.json、summary.json。有通过校验的最终答案时保存 answer.json；未通过的最终候选保存在被事件引用的 artifacts 中，不伪装成最终答案。

config.json 包含模型/预算/协议、问题、实际工具权限与检索配置、快照 ID。事件记录阶段、序号、时间、总耗时、调用 ID、轮数、尝试数、错误和产物引用。请求/响应及大观察放在内容寻址 artifacts 中，JSONL 只放引用和哈希。只保留可见回答/工具动作，去掉 thinking/reasoning 字段和显式 think 段。未知 token 数记 null，记录 API 实际计数，不估算美元成本。

JSONL 每次追加 flush+fsync，最终 JSON 临时文件替换，最后写 manifest。它们不是单一原子事务。被捕获的错误/中断会生成 summary；SIGKILL、断电或磁盘故障无法保证 summary 完成，残留记录只能视作不完整，不允许正常回放。

replay 不恢复执行，不加载 corpus、不实例化模型或资料工具；只读本次日志版本与协议兼容、必需文件齐全、哈希正确、事件连续且以终止事件收尾的记录。模型请求/响应引用必须完整配对。manifest 是损坏检测，不是防恶意篡改的密码学签名。温度 0 仅是生成选项，与确定性回放无关。
