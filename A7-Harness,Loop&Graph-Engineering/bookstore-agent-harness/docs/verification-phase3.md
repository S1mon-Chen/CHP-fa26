# 第三阶段验收记录

验证日期：2026-10-12，macOS / Python 3.12。不是仅凭设计推断。

## 环境与依赖

安装 `python -m pip install -e ".[test]"` 成功；`pip check` 无依赖冲突。

| 组件 | 实际版本 |
|---|---|
| langgraph | 1.2.14 |
| langgraph-checkpoint | 4.2.0 |
| langgraph-checkpoint-sqlite | 3.1.1 |
| langchain-core | 1.6.9 |
| FastAPI / uvicorn | 0.115.12 / 0.34.2 |
| pytest / httpx | 8.3.5 / 0.28.1 |

Docker 本次起初缺少教学镜像，启动接口正确返回 409；使用 README 的 public.ecr.aws 基础镜像构建成功后才进行真实测试，没有静默模拟。

## 已执行验证

`python -m pytest -q`：59 项通过，包括原有 42 项测试。Starlette 测试客户端有一条 anyio 弃用提示，不影响结果。
`node --test tests/test_frontend.mjs`：事件去重、重连、刷新、拒绝与历史游标投影通过。

新增验证涵盖：

- 四个 scripted 场景的 Python/LangGraph 业务观察、候选计数和剩余预算一致。成功场景前者 SUCCESS，后者先 WAITING_APPROVAL。
- 暂停不产生交付、不占活动执行槽；错误哈希/重复批准被拒绝。审批使用 checkpoint 真相，即使 interrupt 后报告尚未写入也能核对恢复。
- 教师拒绝为 REJECTED，理由进入事件与 summary；两个线程互不污染。
- 从安全 LOAD checkpoint 真正复制状态到新 thread；父事件字节不变，子运行重新测试、重新审批。
- 动作收据复用不重复调用模型或扣预算；新候选 VALIDATE 后不会用上一候选文件核对新哈希。
- 候选被篡改显示 RECOVERY_REQUIRED；错误原因持久化。
- 注入交付 manifest 写入后的异常，Resume 仍只有一次逻辑 delivered 事件。
- 框架 recursion_limit 错误转换为 EXECUTION_ERROR，报告保留最新 checkpoint 的候选数。
- CLI/Web 文件锁共用；审批、分支 HTTP 接口；纯历史读取不调用图恢复/对账。
- replay 不调用模型或 Docker 执行器。

## 真正的独立进程与 Docker 重启验证

执行 `python tests/verify_phase3.py`，最新机器可读结果见项目根目录 `phase3-verification.json`。
独立项目为 `runs/phase3-process-6516b6da`，真实后端进程 PID **75124 → 75201 → 75316**；不是同一进程重新创建对象。

| 场景 | 运行 ID | 实际结果 |
|---|---|---|
| 正常停机 / 重启 / 批准 | af592d3ad96247acad44452d39f30a99 | 原 thread，29 passed，SUCCESS，1 次逻辑交付 |
| 独立分支 / 重新测试 / 拒绝 | 2c9e582093b6493f8b7f5aeb21d72df5 | 29 passed，等待自己的审批，再 REJECTED；父记录不变 |
| 第 2 轮 TEST 期间 SIGKILL | d3b57c97c46040e3a0937819fe91050f | 重启显示 RECOVERABLE，核对并删除旧容器，重新测试后 STALLED；候选 2、剩余 1，第 1 轮失败指纹保留 |

强杀夹具只在独立测试项目添加 5 秒等待，不修改项目 starter 或固定测试。恢复后检查旧容器不存在，模型响应总数仍为 2。验证项目里的 backend.log、动作收据、执行记录、SQLite 和事件保留。

## 原 Docker 隔离与业务回归

重新执行 `python tests/verify_docker.py`：初始代码 **26 passed / 3 failed**（包括恰好 100 元等边界）；正确候选 **29 passed**；无限等待以 TEST_TIMEOUT 结束；过大输出以 OUTPUT_LIMIT 结束。没有宿主机候选执行。

## 本地真实模型与 CLI

`bookstore-harness live --engine langgraph` 使用本地 **qwen2.5:7b**。
运行 `bb933135966f4b34aaa1d363c884e797`，第一候选真实 Docker **29 passed**，停在 WAITING_APPROVAL；随后通过 CLI 显式传入审批 ID、候选哈希与备注批准，最终 SUCCESS。本次确实调用 Ollama，token 只保存 API 返回值。

## 浏览器实测

通过真实浏览器访问 127.0.0.1:8766：

- 选择 LangGraph，真实 scripted 运行，SSE 展示 TEST 与候选变化。
- 看到 `>` 改成 `>=` 的 Diff 和 29 passed；刷新后同一等待审批仍可操作。
- 在网页批准运行 `97d327147d0d478fb71d4aeff32e7699`，最终 SUCCESS。
- 选择安全 checkpoint，创建分支 `9d66029936324927b07c88a85720f6de`，展示正确父关系，再次等待审批。
- 填写拒绝备注并拒绝，结果 REJECTED；从头/下一步回放显示“历史轨迹回放”。
- 截图检查了测试、审批、分支输入和 checkpoint 区域。源码、日志、备注均通过 textContent 展示。

浏览器没有测试所有设备尺寸、所有浏览器，也未在浏览器手动执行 SIGKILL；强杀及 Resume 由上述真实独立后端 HTTP 集成程序验证。

## 明确限制 / 尚未覆盖

- 只允许 LOAD 完成、首次 GENERATE 前分支；其他历史边界仅可查看。
- Python 引擎仍是第一阶段行为，没有图审批或图恢复；旧历史记录不能伪造 checkpoint。
- 不支持取消、多 worker、多机或数据库损坏后的自动修复。
- 已注入 TEST 进程退出和交付写入异常；没有穷举每条文件系统指令的断电窗口，也没有真实注入 Docker create 提交不确定窗口。这些不确定情况保守显示 RECOVERY_REQUIRED。
- 模型请求跨崩溃可能重新发送，无法保证 exactly-once 或相同输出；固定 seed 也不是 replay。
- SQLite、事件和产物不是跨存储原子事务；使用收据、哈希及状态核对处理已支持的窗口。
- SIGKILL 遗留的独立临时副本可能保留供检查；容器会先核对再清理，不把 checkpoint 当文件回滚，也不自动删除未经确认的宿主目录。
- 容器不是绝对安全保证，语法检查和子进程超时不是沙箱。
