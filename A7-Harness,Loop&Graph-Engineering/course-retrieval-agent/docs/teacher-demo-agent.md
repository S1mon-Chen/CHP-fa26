# 第二轮约十五分钟教师演示

课前：在项目根目录激活 .venv，运行 `python -m pytest -q`。scripted 不需要 Ollama，但执行真实资料工具。live 需要本机服务和 agent-config.json 中模型；本项目不会下载模型。

所有命令从项目根目录运行。完成后终端显示 run_id，可以用于 inspect/replay。

1. **0–3 分钟：正常回答。** `python -m app.cli agent ask '解释 HDFS 文件块与副本' --mode scripted --scenario single`。观察搜索没有证据 ID，读取才产生 E1；VALIDATE_ANSWER 之后才 COMPLETED。提问：模型生成了一句话，谁决定它可以成为最终答案？
2. **3–5 分钟：跨资料与不足。** 用 `--scenario compare` 演示两次搜索、两次读取、两份证据；用 `--scenario insufficient` 演示 COMPLETED 与 insufficient_evidence 同时出现。提问：工具成功和资料足够有什么区别？
3. **5–8 分钟：越权拦截。** 用 `--scenario unauthorized`。这个场景把一份真实登记的 Flink 文档从可见集合中移除，然后预设模型仍要求读取它。观察 DOCUMENT_NOT_ALLOWED、尝试扣费和没有 EXECUTE_TOOL。再用 `--scenario injection`，观察从虚构资料读到指令后提出路径读取并被拒绝。必须说明：这是受控故障注入，不能证明真实模型总会或总不会受注入影响。
4. **8–10 分钟：无效循环。** 用 `--scenario stalled` 看到相同无命中搜索两次停止；用 `--scenario budget` 看到不同查询继续消耗决策轮直到 BUDGET_EXHAUSTED。提问：为什么不能仅根据工具名重复判定死循环？
5. **10–12 分钟：非法引用。** 用 `--scenario bad_citation`，观察 E99 被拒绝、一次修复后引用 E1。说明修复有上限，且搜索摘要不能冒充读取证据。
6. **12–15 分钟：历史回放与人工评测。** 使用下面命令保存一次运行并回放；关掉 Ollama 也不影响回放。阅读 answer、evidence，逐条判断原文是否真正支持结论。程序没有完成这一步语义判断。

```sh
python -m app.cli agent --json ask '解释 HDFS 文件块与副本' --mode scripted --scenario single > demo-run.json
RUN_ID=$(python -c 'import json; print(json.load(open("demo-run.json"))["summary"]["run_id"])')
python -m app.cli agent inspect "$RUN_ID"
python -m app.cli agent replay "$RUN_ID"
```

live 加演：启动 Ollama，确认 `ollama list` 中有配置模型，再去掉 scripted 参数。观察真实模型未必按预设顺序调用，也可能先读取元数据；不要把它的每一次选择当作最佳策略。

故障区分：MODEL_ERROR 是连接、协议或模型请求问题；TOOL_ERROR 是资料加载/工具内部执行问题；INVALID_ARGUMENTS、DOCUMENT_NOT_ALLOWED 是被控制器返回的动作拒绝；STALLED/BUDGET_EXHAUSTED 是有限执行的终止；资料不足是答案状态。
