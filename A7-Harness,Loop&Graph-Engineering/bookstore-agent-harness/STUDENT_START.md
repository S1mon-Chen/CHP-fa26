# 学生版：从这里开始

解压后在终端进入 `bookstore-agent-harness` 文件夹。建议使用 macOS 或 Linux；当前文件锁与启停脚本不支持原生 Windows，请使用 WSL2。

## 首次安装

需要 Python 3.12。macOS 已安装 Homebrew 时可用 `brew install python@3.12`；依赖首次安装需要网络。

```sh
python3.12 -m venv .venv
.venv/bin/python -m pip install -e ".[test]"
./start.sh
```

浏览器打开 http://127.0.0.1:8765 。如果解压工具未保留执行权限，运行 `chmod +x start.sh stop.sh`。端口占用时使用 `./start.sh --port 8766`。无需再启动另一个前端进程。

## 先体验控制流，再运行真实测试

没有 Docker 或 Ollama：选择 scripted、成功修复，并**显式勾选模拟执行**。这只能演示控制流，显示的模拟测试结果不证明业务代码正确。学生版不包含教师的历史运行；产生自己的记录后才可 replay。

真实 scripted 不调用模型，但需要 Docker Desktop 启动、教学镜像已构建。在项目根目录执行一次：

```sh
docker build -t bookstore-harness:py312 .
```

若 Docker Hub 无法访问，可尝试公共镜像源（仍需要网络）：

```sh
docker build --build-arg BASE_IMAGE=public.ecr.aws/docker/library/python:3.12-slim -t bookstore-harness:py312 .
```

再选 scripted，取消模拟执行。live 还需要安装并启动 Ollama，运行 `ollama pull qwen2.5:7b`；默认配置见 config.json，可选已安装的其他模型。启停脚本不会替你安装依赖、构建镜像或启动/关闭 Docker、Ollama。

## 建议练习顺序

1. Python + scripted 成功修复：观察失败反馈与第二次候选通过。
2. repeated、forbidden、budget：观察 STALLED、校验拒绝、预算耗尽。
3. LangGraph + scripted 成功修复：通过测试后停在等待审批。
4. 等待审批时关闭服务、重新启动；打开原运行并批准。
5. 从安全 LOAD checkpoint 创建分支，重新测试和审批。
6. 回放已有运行，辨别历史观察与本次执行。

```sh
./stop.sh
./start.sh
```

关闭脚本只管理由 start.sh 启动的服务，发送优雅关闭信号。默认等待 30 秒；运行尚未完成时不强杀，可稍后再检查或使用 `./stop.sh --wait 300`。等待审批的记录会保留。它不是任务取消按钮。手动启动的服务仍在原终端 Ctrl+C 关闭。

日志在 `.classroom/server.log`；自己的事件和结果在 `runs/`，checkpoint 在 `.harness/`。不要在运行时删除这些目录。状态查看：`.venv/bin/python scripts/classroom.py status`。

## 阅读代码与测试

macOS 使用 `open docs/code-to-lecture.html` 打开课件对照文档。该文档支持离线阅读与投屏，每条包含原课件页码、真实代码与演示方法。`docs/verification*.md` 是教师此前的历史验收记录，不代表你的电脑已经完成验证。

```sh
.venv/bin/python -m pytest
```

默认只运行 Harness 控制测试；不要把模型生成的代码放到宿主机执行。真实业务测试通过 Docker，具体操作见 README 与教师指南。`starter/bookstore.py` 的边界 Bug 是教学设计，请保留。

## 压缩包内容

包含源码、原始业务文件、固定测试、四个预设场景、Web 静态资源、Dockerfile、依赖配置、启停脚本、教学指南和课件代码对照。附 `PACKAGE_MANIFEST.json`，记录文件 SHA-256。

不包含虚拟环境、缓存、Git 信息、运行记录、SQLite checkpoint、服务 PID/日志、模型权重、Docker 镜像和原始课件 ZIP/全文。需要完整原课件时由教师另行提供。
