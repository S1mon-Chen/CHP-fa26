# 15 分钟教师演示

课前：完成 README 安装和镜像构建，下载配置中的模型；执行 `python -m pytest -q` 和 `python tests/verify_docker.py`。提前打开 TASK.md、starter/bookstore.py、core.py。没有 Docker 时明确全程是模拟观察，不能展示为真实业务验收。

## 0–3 分钟：业务和边界

让学生口算 100 元订单、100 元使用 SAVE10、空购物车总价。强调按折后且舍入后的商品金额算运费。展示 `>` 错误和业务测试；执行 `python tests/verify_docker.py`，看到初始边界失败、正确代码全部通过，以及死循环被终止。

## 3–7 分钟：失败成为下一轮输入

运行 `bookstore-harness scripted --scenario success`。打开输出目录中的 events.jsonl 和 recording.json，对照 LOAD → GENERATE → VALIDATE → APPLY → TEST。第一轮固定测试失败，第二轮模型请求包含 TEST_FAILURE，之后测试通过。展示 changes.diff，说明模型不能自行宣布成功。

## 7–10 分钟：谁控制结束

依次运行 `bookstore-harness scripted --scenario repeated` 和 `bookstore-harness scripted --scenario forbidden`。前者第二次相同代码、相同失败触发 STALLED；后者目标测试路径被拒绝、没有 APPLY/TEST，3 次后 FAILED。用配置副本把 stall_threshold 改成 10 再运行 repeated，观察预算导致 FAILED。`python -m pytest -q` 还覆盖模型超时有限重试、输出上限和无 Docker 不落回宿主。

## 10–12 分钟：真实本地模型

运行 `bookstore-harness live`。指出配置中的地址、模型和超时。等待期间展示只读挂载、禁网、非 root 和资源上限。若模型较慢，使用课前 live 轨迹并清楚说明是历史观察；不保证模型每次修复成功。token 计数来自 Ollama，scripted 为 null。

## 12–14 分钟：回放与调试

运行 `bookstore-harness replay --recording runs/实际成功运行ID/recording.json`。即使 Ollama 和 Docker 停止，回放仍工作。检查 summary 的 historical_observation。对照两次状态节点顺序；解释请求清单变动会拒绝回放，而不是套用不相关结果。

## 14–15 分钟：学生复述

让学生回答：谁提出动作？谁决定可执行路径？测试失败放在哪里？为什么相同错误不会无限循环？历史观察能否证明本次代码执行成功？

结论应由学生给出：模型提出动作，Harness 控制执行、反馈与结束；轨迹支持检查和重现控制流。
