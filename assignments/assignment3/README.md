# 作业 3 书店导购 Agent

学号：524031910668　姓名：陈熹朗

实现了中文政策文档加载、分块、BGE 向量化、余弦检索，以及由真实大模型驱动的 ReAct 工具循环。题目中的“微服务购书 + 拆封退货”案例已运行并保存日志。

## 提交内容

- `BookStore_s4/ASSIGNMENT3.md`：设计说明、五项评分标准对应关系和实测结果。
- `BookStore_s4/backend/src/main/java/com/bookstore/shopping/`：本次新增的 RAG、工具和 Agent。
- `BookStore_s4/backend/src/main/resources/shopping/`：政策原文、模拟书目和两个工具的 Schema。
- `BookStore_s4/evidence-rag/live-complex.txt`：优先查看的完整 Thought → Action → Observation → Final Answer 日志。
- `BookStore_s4/evidence-rag/index-manifest.json`：七个政策块的原文、偏移、文档哈希和实际 512 维向量。
- `BookStore_s4/standalone/`：独立构建入口，只编译本次模块及三个复用的模型客户端类。
- `BookStore_s4/scripts/`：启动、真实交互、检索评测和白名单打包脚本。

提交包只包含相关源码、脚本、配置、说明和验证记录，不包含原始整套工程、JAR、模型权重、node_modules、虚拟环境或密钥。`MANIFEST.sha256` 可检查每个文件内容。

## 解压提交包后独立运行

需要 JDK 17 或以上、Maven，以及 Python 3.10–3.13（实际验证使用 3.12）。首次运行需联网下载 Python/Maven 依赖和约 90 MB 中文模型。后续向量推理在本机 CPU 完成，生成回答仍需要所配置的大模型服务。

进入 `BookStore_s4`，创建虚拟环境并安装依赖：

```bash
python3.12 -m venv .venv-rag
.venv-rag/bin/python -m pip install -r requirements-rag.txt
cp .env.example .env
```

在 `.env` 设置自己的 `LLM_BASE_URL`、`LLM_MODEL`、`LLM_API_KEY`；模型需支持兼容的 Chat Completions 工具调用。不要把 `.env` 提交。此次真实验证使用项目已配置的 DeepSeek `deepseek-flash`。

终端一，运行中文向量服务：

```bash
.venv-rag/bin/python scripts/embedding_server.py
```

终端二，启动独立后端；该脚本会先运行核心测试，再在本机 8083 端口启动：

```bash
bash scripts/run-standalone.sh
```

终端三，运行题目给定案例和无需生成模型的检索评测：

```bash
.venv-rag/bin/python scripts/demo_assignment3.py --base-url http://127.0.0.1:8083
.venv-rag/bin/python scripts/demo_assignment3.py --base-url http://127.0.0.1:8083 --evaluate
```

运行记录默认写入 `evidence-rag/`。若要保留随包附带的记录，用 `--output-dir my-evidence`。按 Ctrl+C 分别停止两个服务。单独测试核心逻辑：`mvn -f standalone/pom.xml test`，不需要密钥、下载模型或运行向量服务。

## 在现有书店中运行网页

仓库中的 `assignments/assignment3/BookStore_s4` 保留了原工程，可直接运行：

```bash
bash scripts/run-assignment3.sh
```

打开 `http://127.0.0.1:5173`，点击右下角“智能书店助手”。启动脚本使用隔离的 H2 演示库，并同时管理 8080 后端、8092 向量服务和 5173 前端；Ctrl+C 停止这三个进程。端口被占用会退出，不会结束其他项目。首次可能需要先安装 Node.js/npm。

若只有提交包，使用上面的独立后端流程即可展示全部评分功能。包中的前端文件是原工程的修改文件，并不是完整 React 工程；需覆盖到作业 2 书店项目后运行 `npm ci` 才能使用原网页。原 `/api/ai/tool-chat`、旧随机向量 `RAGService` 均不是本次作业入口。

## 验证与重打包

完整工程：`mvn -f backend/pom.xml test`；前端：进入 `frontend` 运行 `npm test`、`npm run lint`、`npm run build`。

从 `BookStore_s4` 执行 `python3 scripts/package_assignment3.py`，在上级目录生成 `524031910668-作业3.zip`。脚本采用文件白名单，并拒绝打包依赖目录、运行缓存和凭据文件。
