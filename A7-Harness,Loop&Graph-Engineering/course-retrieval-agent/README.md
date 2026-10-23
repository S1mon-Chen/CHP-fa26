# Course Retrieval Agent：受限课程资料检索 Harness

面向 Python 初学 Agent 的学生。第一轮资料快照、透明检索和三个受限工具保持兼容；第二轮接入本地 Ollama，实现有预算、证据登记、答案校验和回放的 CLI Agent。第三轮增加本地课堂网页，继续复用同一 Harness。没有 MCP、LangGraph 或向量库，不需要 Docker。

## 第三轮：本地课堂网页

在项目目录完成下方安装后，一条命令启动：

```sh
source .venv/bin/activate
python -m app.web
# 等价命令：course-classroom
```

访问 **http://127.0.0.1:8770/**。静态资源全部本地提供，无 CDN、前端构建或在线字体。首次安装依赖需要网络；安装后 scripted 和历史回放可离线使用。live 需要本机 Ollama 与已安装模型，不自动下载。

选择场景并开始：single → compare → insufficient → unauthorized → stalled / budget → bad_citation → injection。scripted 使用预设模型响应与真实资料工具；live 使用真实模型与真实工具。历史表可以查看报告、单步、自动播放、暂停、调整速度和从头开始。点击答案 E1 可定位保存的证据原文。

刷新恢复同一运行展示；关闭浏览器不取消后台运行。**不支持真实运行取消或进程重启续跑**。正常 Ctrl+C 会等待当前有限任务完成；后端异常终止后，未完整落盘记录显示 INTERRUPTED，保留部分事件，拒绝完整回放。完整但损坏记录显示 CORRUPT。回放暂停只停止展示。

服务只绑定 127.0.0.1，校验 Host、Origin 和 JSON 写入；模型地址、资料目录及权限仅由本地可信配置提供。项目级执行锁使网页与 CLI 共享一个执行槽；重复启动请求使用 request_id 幂等处理。不要开放端口给外网；这不是多用户服务。

历史证据只读取该次运行 evidence.json，并验证产物完整性，不读取当前 corpus 替换原文。搜索摘要不是证据，检索分数不是答案置信度；引用合法不代表语义已被证明。

- [15—20 分钟网页演示](docs/teacher-demo-web.md)
- [三个学生练习](docs/student-exercises.md)
- [网页架构与接口](docs/web-architecture.md)
- [第三轮实际验收](docs/verification-web.md)

验证命令（Node 仅用于前端状态单元测试，不是运行依赖）：

```sh
python -m pytest -q
python tests/smoke_cli.py
python tests/verify_agent_cli.py
node --test tests/test_web_state.mjs
python tests/verify_web_restart.py
```

另外提供可选 `tests/browser_e2e.py`；需另行安装 Playwright 和 Chromium，在网页服务运行时执行。它不属于默认 pytest，不会自动下载浏览器。本轮已通过实际浏览器交互检查，独立 Playwright 脚本尚未执行。

## 第二轮：Ollama Agent CLI

继续使用下面的 Python 3.12 安装步骤。先运行测试，再体验不依赖模型的预设场景：

```sh
source .venv/bin/activate
python -m pytest -q
python tests/verify_agent_cli.py
python -m app.cli agent scenarios
python -m app.cli agent ask 'HDFS 文件块与副本有什么关系？' --mode scripted --scenario single
python -m app.cli agent ask '比较 Spark 和 Flink 事件时间与水印' --mode scripted --scenario compare
python -m app.cli agent ask '某具体版本的精确吞吐量是多少？' --mode scripted --scenario insufficient
python -m app.cli agent ask '演示越权' --mode scripted --scenario unauthorized
python -m app.cli agent ask '演示循环' --mode scripted --scenario stalled
python -m app.cli agent ask '演示预算' --mode scripted --scenario budget
python -m app.cli agent ask '演示非法引用修复' --mode scripted --scenario bad_citation
python -m app.cli agent ask '演示虚构资料注入' --mode scripted --scenario injection
```

另有 unknown_tool 场景。scripted 是**预设模型响应 + 真实资料工具**，不是根据任意问题生成答案的模型；场景模板只用实际工具结果中的 ID。injection 明确使用 tests/fixtures/injection/ 中的虚构资料，不修改正常 corpus；unauthorized 明确缩小当前可见集合。

真实运行前，启动已安装的 Ollama，检查模型：

```sh
ollama list
# 如果服务尚未运行，可以在另一个终端执行 ollama serve
python -m app.cli agent ask '请解释 HDFS 文件块与副本的关系。' --mode live
python -m app.cli agent ask '比较 Spark Structured Streaming 和 Flink 的事件时间与水印，分别读取两方资料并引用证据。' --mode live
```

可信 agent-config.json 指定 model、url、超时和预算；默认模型 qwen2.5:7b，地址 http://127.0.0.1:11434。只能连接本机 HTTP。无可用模型时明确 MODEL_ERROR，不自动下载，不静默切换 scripted。可以将 model 改为自己已安装且支持工具调用的模型。每次 live 先检查安装状态并发出真实工具调用探针。

`agent` 后的全局参数放在 ask/inspect/replay 之前，例如 `agent --json --agent-config my-agent.json --config restricted.json ask ...`。第一轮 `--config` 仍控制资料与可见范围，第二轮配置不能代替资料权限配置。

默认 8 决策轮、10 次额度内工具尝试、每轮最多 3 调用、请求 60 秒、整次 180 秒。非法参数与越权也耗费额度。超出剩余额度的提议单独计数并拒绝，不执行。模型传输重试、最终生成、一次修复都计入总期限。预期 STALLED、BUDGET_EXHAUSTED 等命令退出码为 1，不是脚本卡死。

保存 JSON 并查看、回放（不需要 jq）：

```sh
python -m app.cli agent --json ask '解释 HDFS 副本' --mode scripted --scenario single > demo-run.json
RUN_ID=$(python -c 'import json; print(json.load(open("demo-run.json"))["summary"]["run_id"])')
python -m app.cli agent inspect "$RUN_ID"
python -m app.cli agent replay "$RUN_ID"
```

replay 只读取完整历史记录，不调用模型或工具，不要求当前 corpus 存在。run_id 必须有效；记录缺失、损坏或版本不兼容则拒绝回放。runs/<run_id> 内保存配置、事件、模型交互引用、证据、通过验证的答案、总结及完整性清单；大文本在 artifacts/。未通过验证的答案候选也保存在引用产物中。

最终答案 answered / partial / insufficient_evidence 与运行状态分开。COMPLETED 只表示完成流程；程序校验引用来自真实读取，不保证结论语义正确。实际跨资料回答存在内容遗漏，见 [第二轮验收与回答评测](docs/verification-agent.md)。

常见问题：模型未安装时修改配置选择已有模型；NATIVE_TOOL_PROBE_FAILED 表示未生成合法原生调用；MODEL_REQUEST_TIMEOUT 可检查模型速度/配置，但总期限仍生效；READ_EVIDENCE_REQUIRED 表示搜索摘要不能用于最终引用；REPLAY_INVALID 表示不能将不完整轨迹伪装为完整回放。

参考 [第二轮架构](docs/agent-architecture.md) 与 [十五分钟演示](docs/teacher-demo-agent.md)。Harness 限 macOS/Linux 主线程；网页在独立子进程的主线程运行同一 Harness，避免阻塞 Web 事件循环。尚未支持 Windows。

## macOS 安装

安装 Python 3.12 后，在终端运行（首次安装依赖需要网络）：

```sh
cd /Users/haopengchen/Documents/ChatGPT/AgentHarness/course-retrieval-agent
python3.12 -m venv .venv
source .venv/bin/activate
python -m pip install -e ".[test]"
python -m pytest -q
python tests/smoke_cli.py
```

若复制给学生，第一行替换为学生的解压目录。依赖固定为 Pydantic 2.14.1、FastAPI 0.115.12、Uvicorn 0.34.2；测试依赖 pytest 8.3.5、HTTPX 0.28.1。支持 `python -m app.cli` 和安装后的 `course-retrieval`。采用可编辑安装，使 corpus/config/docs 与代码保持在同一教学项目内；不将本项目当作单独 wheel 的内置资料分发。

## 按顺序体验 CLI

全局 `--json`、`--config` 参数放在子命令之前。正常返回退出码 0，工具或加载错误为 1，CLI 命令行语法错误为 argparse 的 2。

```sh
python -m app.cli check
python -m app.cli snapshot
python -m app.cli list
python -m app.cli list --topic Flink --limit 2
python -m app.cli search 'HDFS 副本'
python -m app.cli search 'Spark Stage Task'
python -m app.cli search 'Flink 水位线'
python -m app.cli search '迟到消息'
python -m app.cli search 'RowKey 多版本'
python -m app.cli search 'zzzznonexistent987'
python -m app.cli --json search 'HDFS 副本' --top-k 3
```

最后一个英文查询确定无命中。可读输出会显示实际检索词、得分、词贡献和原文片段；JSON 还包含哈希等全部元数据。得分是词频加权，不是答案正确概率。

当前示例中，HDFS 查询的元数据片段可能因标题重复而排名第一。继续读取命中的“基础概念”片段，才看到文件块与副本的正文。这也是一个有意保留的教学观察：排名靠前不等于证据充分。

以下 ID 来自随项目交付的资料快照，可直接复制；修改资料后请重新搜索取得新的 chunk_id：

```sh
python -m app.cli read doc_3ff3a51fb3b1b173 chunk_cd000f6ba114f3af09d6d1b7
python -m app.cli --json read doc_3ff3a51fb3b1b173 chunk_cd000f6ba114f3af09d6d1b7
python -m app.cli search '副本' --document-id doc_3ff3a51fb3b1b173 --top-k 2
python -m app.cli --json schema > tool-schemas.json
```

read 返回完整片段；snippet 只是搜索摘录。行号从原文件第 1 行计数，Markdown 标记、空行保留；字符区间用于定位单行被切开的情况。

## 看见结构化拒绝

下列命令预期退出码为 1，并输出合法的错误 JSON，不执行任何 shell 或读取任意文件：

```sh
python -m app.cli --json invoke unknown_tool '{}'
python -m app.cli --json invoke list_documents '{"limti":3}'
python -m app.cli --json invoke search_documents '{"query":"HDFS","top_k":6}'
python -m app.cli --json invoke read_document '{"document_id":"../../etc/passwd","chunk_ids":["chunk_000000000000000000000000"]}'
```

错误码包括 UNKNOWN_TOOL、INVALID_ARGUMENTS、DOCUMENT_NOT_ALLOWED、CHUNK_NOT_FOUND、CHUNK_DOCUMENT_MISMATCH、CORPUS_LOAD_ERROR、OUTPUT_LIMIT_EXCEEDED。校验错误不回显原参数、绝对路径或未授权内容。

## 教师配置与权限实验

config.json 是可信配置，不应交给模型改写。corpus 只相对此配置文件所在目录解析，不扫描工作区其他目录。默认上限：单文件 128 KiB、资料集 1 MiB、工具返回 32 KiB。

`allowed_document_ids: null` 表示允许当前资料集所有登记文档；`[]` 表示无权限；显式列表表示只能访问这些文档。以下在项目根目录创建一个单文档配置，不改原配置或资料：

```sh
python - <<'PY'
import json
from pathlib import Path
config = json.loads(Path('config.json').read_text())
config['allowed_document_ids'] = ['doc_3ff3a51fb3b1b173']
Path('restricted.json').write_text(json.dumps(config, ensure_ascii=False, indent=2))
PY
python -m app.cli --config restricted.json list
python -m app.cli --config restricted.json search 'Flink 水位线'
python -m app.cli --json --config restricted.json invoke search_documents '{"query":"HDFS","document_ids":["doc_0000000000000000"]}'
```

list/search/read 共享同一权限集合。路径、URL、命令都不能作为 ID；只能通过内存快照中的已登记文档定位片段。工具不重新打开资料。若想观察输出超限，可在临时配置中把 output_limit 设为 256，再读取完整片段；它返回明确错误，不截断正文。

这只是工具访问控制，不保护用户直接从本机文件系统读文件，也不宣称解决所有 Prompt Injection 或回答幻觉。教师应保持配置与目录可信，加载期间不并发修改资料。

## 示例资料与阅读顺序

corpus 中 8 份 Markdown 均为中文教学示例，每份约 760–865 Unicode 字符；TXT 同样支持，单元测试包含 TXT。保留具体版本精确性能数字、完整运维手册等资料缺口。英文技术词、中文双字组与小型同义词表使命中过程可解释，无需下载分词词典。

- `app/corpus.py`：加载、切分、行号、快照与稳定 ID。
- `app/search.py`：词项与评分。
- `app/schemas.py`：严格参数与结果类型。
- `app/tools.py`：固定注册表和统一 `ToolService.invoke_tool(name, arguments)`。
- `app/cli.py`：教师配置、命令行与脱敏错误。
- [设计说明](docs/design.md)：精确切分、公式、快照和权限契约。
- [官方来源核对](docs/sources.md)：资料依据和版本边界。
- [十分钟教师演示](docs/teacher-demo.md)。
- [下一轮 Ollama 接口说明](docs/ollama-next.md)。
- [实际验收记录](docs/verification.md)与 [CLI 原始 JSON 记录](docs/smoke-results.json)。

没有搜索到内容与搜索到内容但证据不足，都是正常结果；不要把工具 ok=true 当作答案已被证明。

## 课堂启停、学生分发与课件代码导读

安装依赖后，运行 `./start.sh` 在后台启动课堂服务；`./stop.sh` 优雅关闭脚本管理的服务。脚本可从任意目录执行，不自动安装或下载模型，也不关闭 Ollama。重复启动不会创建新服务。

```sh
./start.sh
# http://127.0.0.1:8770/
.venv/bin/python scripts/classroom.py status
./stop.sh
```

可指定 `./start.sh --port 8771`。已有手动启动的服务占用端口时，脚本会报告冲突，不接管或杀死其他进程；先在原终端 Ctrl+C。停止仅向身份匹配的本项目进程发送 SIGTERM，等待任务结束，默认 30 秒；`./stop.sh --wait 240` 可等更久，超时不强杀。PID、锁和日志保存在 `.classroom/`，不分发给学生。

[课件—代码—演示 HTML](docs/code-to-lecture.html) 可直接双击离线打开；对应 A7 实际 40 页课件，包含 18 个主题条目、真实代码行号和摘录。没有实现的 checkpoint、HITL、多 Agent、沙箱等单独标注，不混同历史回放与恢复。

生成学生包：`.venv/bin/python scripts/package_student.py`。输出 `dist/course-retrieval-agent-student.zip` 和 SHA-256。包内含代码、8 份资料、配置、前端、测试 fixture、文档、启停脚本及 9 个完整 scripted 历史，学生没有 Ollama 也可回放。排除虚拟环境、缓存、进程文件、安装元数据、其他历史和本机 live 记录，不重复附带原课件。包内 `STUDENT_PACKAGE.json` 列出每个文件哈希与筛选规则。

学生解压后进入 `course-retrieval-agent`，执行：

```sh
python3.12 -m venv .venv
source .venv/bin/activate
python -m pip install -e ".[test]"
./start.sh
open http://127.0.0.1:8770/
open docs/code-to-lecture.html
# 下课时
./stop.sh
```

依赖首次安装需要网络；不包含 Ollama 程序、模型权重或 Python 本身。包内历史可以直接回放；live 验收文档提到的教师原始记录不在学生包中。
