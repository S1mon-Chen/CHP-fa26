# 第三轮架构与接口

## 一套 Harness，两种入口

CLI agent ask → app/runner.py → app/agent.py Harness。
网页 POST /api/runs → app/web.py Manager → 固定 app.web_worker 子进程 → 同一 runner 与 Harness。

子进程主线程保留原有 SIGALRM 请求、工具和总期限控制，模型与检索不阻塞 Web 事件循环。不是另一套 Agent 循环。只支持 macOS/Linux。fcntl 项目级锁限制一个活动执行，网页线程锁保护启动；request_id 保存幂等请求，相同 ID 不创建重复运行。服务锁防止同项目启动多个网页服务。

## 事件与落盘

网页读取 Harness 原有结构化 events.jsonl 和按哈希引用的 artifacts，绝不解析终端输出。SSE id 等于 seq；after 与 Last-Event-ID 取较大值补发，前端 Map 按 seq 去重。半行日志暂不发送。指标取后端累计值，不因补发重复相加；完成后从 summary 核对。

历史完整回放沿用 recording.replay 的协议和完整性校验。历史证据取 evidence.json，不用当前 corpus。模型原始思维字段原本就不持久化，网页也不展示它们。中断没有完整 manifest，展示部分事件和 INTERRUPTED；损坏则 CORRUPT，不补造答案或证据。

正常退出等待有界任务结束。后台监护管道在后端异常退出时关闭，执行子进程退出，释放执行锁；重新启动不会自动续跑。浏览器断开不影响后端。无真实取消功能。

## 固定接口

- GET /api/status：资料可用性、Ollama 连接和安装模型、活动运行。
- GET /api/scenarios：实际 scripted 场景与示例问题。
- POST /api/runs：严格 JSON；request_id、question、mode、scenario、model、max_decisions、max_tool_attempts；未知字段拒绝。仅 live/scripted 启动执行。
- GET /api/runs：运行列表。
- GET /api/runs/{run_id}：状态、报告和已保存产物。
- GET /api/runs/{run_id}/events：SSE，after / Last-Event-ID 补发。
- GET /api/runs/{run_id}/answer：保存的答案。
- GET /api/runs/{run_id}/evidence：保存的证据集合。
- GET /api/runs/{run_id}/evidence/{evidence_id}：E1 等已登记证据。
- GET /api/runs/{run_id}/replay：完整历史事件及允许展示的引用内容。

run_id 为 32 位小写十六进制，证据 ID 为 E 加正整数。不存在返回 404，非法 ID 返回 400，校验失败 422，忙碌/不可回放 409，依赖不满足 503。

## 本地边界

默认 127.0.0.1:8770；Host 只接受 localhost / 127.0.0.1 的配置端口，Origin 若存在必须同源。不开放 CORS；写入只收 JSON。浏览器不能给定服务 URL、资料路径、命令或产物路径。目录与引用校验阻止越界和符号链接，模型和工具权限继续由原配置控制。

前端使用 textContent/createElement，未使用不可信 innerHTML；CSP 限制同源资源、禁止对象和嵌套框架。无外部字体、CDN 或 Markdown HTML 渲染。可信本地文件仍须由教师管理；这不是多用户安全隔离服务。

## 展示语义

action_proposed 只表示提议；tool_accepted / tool_rejected 表示校验；仅 tool_started 表示真正开始执行。搜索结果不登记 E ID。读取后由 Harness 登记；重复读取复用证据。执行状态与答案状态分开。

自动播放计时器只控制历史展示进度，不能代表本次执行。state.mjs 负责确定性事件归并，app.js 负责安全 DOM、SSE 与历史控制。
