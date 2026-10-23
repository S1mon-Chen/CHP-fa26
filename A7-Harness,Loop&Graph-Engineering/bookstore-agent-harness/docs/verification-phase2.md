# 第二阶段实际验收

日期：2026-10-12。使用项目 Python 3.12 虚拟环境、FastAPI 0.115.12、Uvicorn 0.34.2、本地 Docker 29.6.1 和 Ollama `qwen2.5:7b`。

## 自动化检查

- `python -m pytest -q`：**42 passed**，包含原第一阶段 20 项控制测试与第二阶段 22 项。第三方 Starlette/AnyIO 有一个弃用警告，不影响测试结果。
- `node tests/test_frontend.mjs`：事件序号去重、重连后的合并、刷新恢复、被拒候选投影和历史游标通过。Node 仅用于开发测试，网页运行不需要 Node。
- `node --check web/app.js`、`node --check web/state.js`：通过。
- 参数范围、未知字段/命令/URL、并发启动、重复请求及跨服务重启幂等性、符号链接越界、非法 run_id、跨站请求、缺少依赖提示均有测试。
- SSE 内容与磁盘 events.jsonl 完全一致；Last-Event-ID=5 时只补发后续事件；空补发、非法序号也已验证。
- 回放测试将执行函数和依赖探测替换为“一旦调用就失败”，仍成功读取记录；不创建新工具执行。
- 候选中的 HTML 字符串在 API 中作为 JSON 数据返回；前端代码/日志绑定仅使用 textContent，无 innerHTML。

## 真实 HTTP / SSE / Docker / Ollama 链路

保持 `python -m harness.web` 运行后执行 `python tests/verify_web.py`。持久化汇总在 `web-verification-phase2.json`。

| 场景 | 实际结果 | run_id |
|---|---|---|
| scripted 成功 | 第 1 轮真实测试 3 failed / 26 passed，第 2 轮 29 passed，SUCCESS | `211a94b7602846af9641f3d236a3c1e7` |
| scripted 重复错误 | 第 2 轮 STALLED | `d57209a664e547e29f79e041bca62f3a` |
| scripted 违规修改 | 3 次拒绝，0 次 TEST，FAILED | `e1168935b0d0498bb441cafaa9c127e5` |
| scripted 预算耗尽 | 3 个不同候选哈希，3 轮真实测试失败，FAILED | `520231a818664092bd3cfb7e72f09045` |
| 显式模拟 | SUCCESS；计数/退出码等真实统计为空 | `2124c7eeaff04ccc986ddfebb4ca5870` |
| 网页 replay | 读取第一行同一运行，无新 run_id、无模型/工具执行 | `211a94b7602846af9641f3d236a3c1e7` |
| live | 真实 Ollama，第 1 轮 29 passed，SUCCESS | `09d09524c9c54ef7825c80800edc793e` |

逐项核对了真实退出码、测试耗时、输出截断标记与候选 diff。每次 HTTP 实验同时验证完整 SSE 以及从第 5 个事件后的补发。

## 第一阶段兼容

重新运行 `python tests/verify_docker.py`，初始边界失败、修复后全通过、死循环超时和输出上限均通过，日志为 `phase2-docker-verification.log`。

原 CLI `bookstore-harness scripted --scenario success` 仍成功；新增 budget 场景显式模拟按预算 FAILED。旧 live 录制通过 CLI replay 成功，新记录为 `d0f1ca288a484ba2af2091727b4c1ae3`，原文件未修改。

## 实际浏览器检查

在 Codex 内置浏览器打开 http://127.0.0.1:8765，检查了浅色布局、中文控件和投屏文字。

- 实际点击 scripted 开始，收到 SUCCESS、29 项通过、退出码 0、真实耗时和 SSE 完成标记；查看到 `>` → `>=` 的 diff。该次运行 `40cbf5d0ac2a4801a05595cfc9143377`。
- 实际点击违规场景，显示 Harness 已拒绝、请求测试文件的路径与拒绝原因，测试区显示“尚未测试”。
- live 在 GENERATE 期间刷新，前后 run_id 均为 `0fa6dca2d6f74a34943eb5564839c0fa`，SSE 恢复连接。
- 检查从头开始、下一步、自动播放、暂停和速度选择。播放使用旧事件，不修改原时间。
- 加载第一阶段 live 历史 `677a69650b804d209cbbaad1ec53ff70`：历史来源标注正确，旧记录没有的测试计数/退出码/耗时/截断信息均为“未提供”。

## 现有限制和未测范围

不支持取消运行；只支持单个 Web 服务进程内的一次活动运行。正常关闭等待 Harness 完成；强杀服务后只保留已有记录，不自动续跑。

未覆盖 Safari/Firefox 等其他浏览器和手机实机；浏览器网络断开补发由真实 HTTP/SSE 与前端合并测试覆盖，未在浏览器中人为切断网络。缺少 Docker/Ollama 通过依赖故障注入验证，没有停止或卸载用户服务。HTML 数据路径和 textContent 绑定有自动化检查，未向用户浏览器注入恶意页面脚本。容器安全边界仍遵循第一阶段说明，不保证绝对安全。
