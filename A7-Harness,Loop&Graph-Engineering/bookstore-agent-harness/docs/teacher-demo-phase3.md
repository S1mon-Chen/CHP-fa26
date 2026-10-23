# 第三阶段：20 分钟讲清持久化、审批与恢复

## 课前检查

在项目目录激活 Python 3.12 环境，执行 `python -m pip install -e ".[test]"`。
确认 `docker info` 和 `docker image inspect bookstore-harness:py312` 成功，再运行 `python -m harness.web`。打开 http://127.0.0.1:8765。
本节以 scripted + 真实 Docker 为主，不依赖 Ollama 生成质量。live 需要 config.json 的本地模型，课堂前先用 `ollama list` 检查。

运行 `python -m pytest -q`；有 Docker 时运行 `python tests/verify_phase3.py`。后者在独立测试项目启动三个真正的后端进程，包含一次 SIGKILL，输出 phase3-verification.json。不要用正在授课的运行做强杀实验。

没有 Docker 时，勾选“显式使用模拟工具结果”，并向学生指出“模拟结果”标识。已有历史记录可以直接 replay，无需模型或 Docker。回放不证明本次执行成功。

## 两种编排的对照

| 项目概念 | Python 引擎 | LangGraph 引擎 |
|---|---|---|
| State | `core.run` 中的普通 dict | `graph_engine.HarnessState` TypedDict |
| Node | 循环调用 `Runtime.step` | `build` 注册 LOAD / GENERATE / VALIDATE / APPLY / TEST，调用同一个 `Runtime.step` |
| Edge | while 循环读取 state.next / status | add_conditional_edges、add_edge、START / END |
| 成功 | 测试通过即 SUCCESS（原阶段行为） | TEST → APPROVAL，interrupt 等待 → DELIVER → SUCCESS |
| 校验、预算、失败检测 | 共用 runtime.py、core.validate | 同一实现 |
| 模型、真实测试 | runner 装配 Ollama / DockerExecutor | 同一适配器 |
| 恢复 | 原阶段没有图恢复 | SqliteSaver + thread_id + 动作收据 |

先指向 `runtime.py` 的 failed_status 和 step，再打开 `graph_engine.py`。让学生看到变化集中在编排层。框架没有替代业务契约或隔离边界。

## 演示顺序

### 0–4 分钟：相同修复任务、不同结束条件

选择 LangGraph、scripted、成功修复、预算 3、不勾模拟。第一候选保留边界 Bug，真实测试失败；第二候选改成 `>=`，29 项测试通过，停止于 WAITING_APPROVAL。

点 Diff，指出只改了一个比较符号。检查候选哈希、实际测试标识。此时本地没有 delivery 目录。

提问：为什么模型说“完成了”不算成功？为什么测试通过仍未交付？Python 引擎的 SUCCESS 与审批后的 SUCCESS 有什么教学区别？

### 4–8 分钟：暂停、真正关闭进程、继续审批

保留当前 run_id。后端终端 Ctrl+C；重新执行 `python -m harness.web`。刷新网页，或从历史区选择该 run_id，点击“打开 / 恢复审批”。仍为同一 thread、候选和审批请求。

填写备注并批准。观察 APPROVAL → DELIVER → SUCCESS。交付仅为 `runs/<id>/delivery/bookstore.py`、changes.diff 和 manifest.json。检查 starter 未被覆盖。

重复点击/重发审批应被拒绝，只有一次逻辑 delivered 事件。没有审批结果时不能进入图 SUCCESS。

提问：保存网页内存能做到进程重启恢复吗？若候选哈希变了，旧批准还能用吗？

### 8–12 分钟：checkpoint 历史与独立分支

展开 checkpoint 列表，解释每项显示时间、下一节点、候选、最近测试状态和 ID。只有“LOAD 完成 · 首次生成前”允许创建分支。

选择安全 checkpoint，填写“比较一个候选的预算”，把顶部预算改成 1，Fork。新 run_id / thread_id 关联父运行；第一候选失败后预算结束，父运行仍为 SUCCESS。

再次从父安全边界 Fork，预算 3。分支重新测试并等待自己的审批。拒绝并填写理由，观察 REJECTED。拒绝后没有自动重试；想再修复必须新建分支。

代码对应 `service.fork`：获取父快照 values，复制后替换新身份、预算与父关系；`graph.update_state(new_thread_config, copied, as_node='LOAD')` 创建子线程状态，然后继续。没有把父 checkpoint_id 当作子线程 checkpoint。

提问：为什么只允许这个边界？从任意 TEST 后分支需要恢复哪些文件和外部状态？

### 12–16 分钟：进程中途退出与副作用

展示 `phase3-verification.json` 和对应测试运行。验证程序在第 2 轮 TEST 正在 Docker 中运行时 SIGKILL 后端；第 1 轮失败已进入 checkpoint。

重启后显示 RECOVERABLE，点击 Resume。Harness 核对容器标签及归属、终止旧执行，再用全新副本重试。最终 STALLED，候选仍为 2，剩余预算 1；失败指纹未清空。

指向 `actions/` 的稳定动作 ID、`calls/` 的调用结果收据、`execution-2.json` 以及 interrupted_execution_cleaned 事件。若 Docker create 提交窗口无法确认或产物哈希不符，显示 RECOVERY_REQUIRED，说明具体原因。

提问：模型请求发送成功、结果还没落盘就断电，能否保证不再调用？答案是不能。日志必须明确恢复重试可能得到不同结果。固定随机种子不能消除服务版本、硬件、并发、采样实现和外部工具变化；确定性 replay 来自已录制结果与请求匹配。

### 16–20 分钟：replay、resume、fork 的边界

用“查看报告 / 回放”进入只读历史，从头逐步播放；改变速度，原始事件时间不变。回放时批准按钮禁用，不能把历史候选当作当前待批准请求。

- replay：读取旧观察，不调用工具。
- resume：继续原 thread，不重置预算，可能重新执行未完成动作。
- fork：新 thread，从安全状态开始独立实验，重新测试/审批。

Checkpoint 只恢复图状态。它不会撤销已发送的邮件、外部提交或旧容器的执行；本项目通过容器归属核对、独立副本、收据和幂等本地交付管理副作用。三个文件系统产物与 SQLite 不是天然原子一致，报告须对账。

## 读懂错误

- MODEL_ERROR：模型调用有限重试耗尽。
- VALIDATE / rejected：Harness 拦截越界路径、结构或语法，预算允许才继续。
- TEST / TEST_FAILURE：业务测试失败，可以反馈给下一候选。
- EXECUTION_ERROR：Docker、超时、输出上限或图框架异常，不伪装业务失败。
- STALLED：相同候选哈希与相同规范化失败达到阈值。
- FAILED：候选预算耗尽。
- RECOVERY_REQUIRED：无法安全自动继续，先检查明确原因或创建独立分支。
- REJECTED：教师明确拒绝，理由进入审计记录。

## 学生练习

1. 比较两个引擎在 repeated 场景的候选数和剩余预算，解释为什么事件条数不必相同。
2. 从安全 checkpoint 分出预算 1 与 3 的两个实验，核对父运行文件没有改变。
3. 提交错误候选哈希的批准请求，找出后端拒绝位置。
4. 只在测试项目篡改候选文件，观察 RECOVERY_REQUIRED；解释为什么不能直接交付。
5. 画出 TEST 通过到 SUCCESS 的所有节点，指出 interrupt 前哪些动作必须能重复。

## 官方 API 依据

- [Interrupts：节点恢复从开头重执行与 Command(resume=...)](https://docs.langchain.com/oss/python/langgraph/interrupts)
- [Persistence：thread_id、快照及历史](https://docs.langchain.com/oss/python/langgraph/persistence)
- [Time travel：update_state 与分支](https://docs.langchain.com/oss/python/langgraph/use-time-travel)
- [Checkpoint API：SQLite saver](https://reference.langchain.com/python/langgraph/checkpoints)

本项目已实际验证的版本见 pyproject.toml；升级框架后应重新运行独立进程恢复测试。
