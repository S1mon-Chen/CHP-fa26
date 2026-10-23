# Bookstore Agent Harness

面向已经掌握 Python 的学生，用一个运费边界 Bug 学习：模型只提出候选，Harness 决定是否接受、在哪里执行、何时停止。无需付费 API，第一版是普通 Python 显式状态机，没有网页或 LangGraph。

## macOS 安装

先安装 Python 3.12、Docker Desktop 和 Ollama，并启动 Docker Desktop。以下命令从本项目目录运行：

```sh
python3.12 -m venv .venv
source .venv/bin/activate
python -m pip install -e .
docker build -t bookstore-harness:py312 .
ollama list
# 如果 config.json 中的模型尚未下载：
ollama pull qwen2.5:7b
# 如果 Ollama 未运行，在另一终端执行 ollama serve
python -m pytest -q
python tests/verify_docker.py
```

`config.json` 提供模型名称、HTTP 地址、调用超时、额外调用重试次数、候选预算、重复阈值、容器镜像和执行限制。默认最多 3 个候选，每个候选模型调用最多 2 次。修改配置文件或用 `--config /absolute/path/config.json` 指定另一份。不估算 token；仅记录 Ollama 返回的真实计数，无计数为 null。

本项目采用源码目录 + editable install 运行；业务夹具和说明保存在仓库中，不支持脱离仓库安装 wheel 后运行。

## 三种模式

```sh
# 预设响应，但在 Docker 内真实执行固定测试：
bookstore-harness scripted --scenario success
bookstore-harness scripted --scenario repeated
bookstore-harness scripted --scenario forbidden

# 真实 Ollama + 真实 Docker：
bookstore-harness live

# 无 Docker 时显式选择模拟控制流：
bookstore-harness scripted --scenario success --simulate

# 替换 RUN_ID 为上一步输出的目录名；使用原来的配置：
bookstore-harness replay --recording runs/RUN_ID/recording.json
```

`success` 场景先交付原始错误代码，收到测试反馈后交付正确代码；`repeated` 重复相同失败，第二次触发 STALLED；`forbidden` 尝试修改测试文件，3 次被拒后 FAILED。非 SUCCESS 的 CLI 返回码为 1，教学中的预期失败不是程序崩溃。

`--simulate` 只用于 scripted，终端和日志标为 simulated；预设测试观察不能证明业务正确。未指定它时 Docker 不可用会记录 EXECUTION_ERROR，绝不执行宿主候选代码。live 不允许模拟。replay 标为 historical_observation，不连 Ollama、不探测 Docker、不重复执行任何工具；按录制请求逐项匹配，同时校验任务、初始代码、固定测试和配置清单。修改这些文件会拒绝旧录制。录制文件不是签名证据，不应接收不可信来源后当成实测证明。

## 结构和流程

- `TASK.md`：输入契约、Decimal 返回值与舍入规则。
- `starter/bookstore.py`：只有 `>` 应为 `>=` 这一处 Bug。
- `tests_business/`：固定业务测试，模型不可修改。
- `harness/core.py`：状态机、校验、预算、失败指纹和录制/回放。
- `harness/executor.py`：固定 Docker 测试命令、资源限制、超时及输出上限。
- `harness/ollama.py`：标准库 HTTP 客户端。
- `scenarios/`：完整预设模型响应和显式模拟观察。
- `tests/`：控制逻辑测试与真实 Docker 验收程序。
- `docs/teacher-demo.md`：约 15 分钟教师演示。

```text
LOAD → GENERATE → VALIDATE → APPLY → TEST
         ↑          │                 │
         └─ 校验失败或测试失败且可继续 ─┘
TEST 通过 → SUCCESS
候选预算耗尽 → FAILED
同代码哈希 + 同规范化失败摘要达到阈值 → STALLED
模型调用重试耗尽 → MODEL_ERROR
执行超时、输出超限、Docker/测试基础设施异常 → EXECUTION_ERROR
```

重复阈值与预算同时满足时优先 STALLED。违规响应也消耗候选预算；模型传输重试不另占候选预算。测试超时属于执行错误，立即终止。每次 GENERATE 都包含任务、初始代码、上一候选和结构化反馈。完整替换代码仅接受 JSON 的 path/code 字段，检查目标路径、UTF-8 字节长度和语法。不使用 eval/exec 解析响应。

每个 `runs/<run_id>/` 包含 `events.jsonl`、`summary.json`、`recording.json`、有效的最终 `candidate.py`、每轮候选、受限测试输出和 `changes.diff`。全部候选无效时没有最终候选且 diff 为空。事件只放哈希、摘要和元数据，完整代码放在候选/录制中。回放创建新 run_id，来源仍保留为历史观察。事件中的耗时为该操作的本次耗时；回放耗时不代表历史模型执行耗时。

## 隔离边界

每次真实测试在全新临时项目副本中运行，Harness 复制固定测试。整个工作目录只读；容器非 root、根文件系统只读、无网络、无 capabilities、no-new-privileges，限制 256 MiB 内存、1 CPU、64 进程，32 MiB 临时目录。只挂载本轮候选和固定测试，不挂载项目、密钥或 Docker socket。时间和输出上限由宿主控制器监督，结束后强制清理容器。构建镜像时允许下载 pytest，执行阶段禁网。

AST 检查只检查语法，不是安全沙箱；超时也不是安全沙箱。容器不是绝对安全保证。此项目用于可信本地课堂，不应直接扩展为公网任意代码执行服务。固定测试只读不等于不可被恶意 Python 进程干扰；同进程测试也不证明任意恶意候选正确，适用于本教学修复任务。

## 常见问题

- Docker 连接失败：启动 Docker Desktop，确认 `docker info` 成功。没有 Docker 时仅用显式模拟或回放。
- 镜像不存在：先执行上面的 `docker build`；运行时不会偷偷安装依赖。
- Ollama 连接失败：确认服务运行、`ollama list` 中有配置的模型，并检查 URL。首次加载模型较慢时调大 model_timeout。
- live 没修好：模型输出不是事实，以 pytest 为准；检查事件、每轮输出及 diff。不同模型运行不保证同样结果。
- 修改配置后 replay 拒绝：恢复录制使用的配置；严格请求匹配是为了避免错放轨迹。
- 默认 `pytest` 只运行 Harness 测试。初始业务故意失败，请用 Docker 验收脚本验证；不要在宿主导入模型候选。

详细实测结果见 [验收记录](docs/verification.md)。若 Docker Hub 连接被重置，可使用本次已验证的公共仓库构建命令：

```sh
docker build --build-arg BASE_IMAGE=public.ecr.aws/docker/library/python:3.12-slim -t bookstore-harness:py312 .
```

预算耗尽演示也可直接使用已提供的配置：

```sh
bookstore-harness scripted --scenario repeated --config docs/config-budget.json
```
