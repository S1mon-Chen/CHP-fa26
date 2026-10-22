# 作业 2 电子书店 Function Calling

学号：524031910668　姓名：陈熹朗

本次在已有 BookStore_s4 项目中加入库存与竞品比价助手。真实模型通过兼容 Chat Completions 的 HTTP API 选择工具；Java 后端解析请求、查询数据库或模拟竞品服务，再将执行结果回传给模型。前端显示模型的最终回答，并可展开查询记录。

## 提交内容

`524031910668-作业2.zip` 是增量提交包，仅收录本次新增和修改的源码、配置、脚本、说明及验证记录。解压后，将 `BookStore_s4` 下文件按相对路径覆盖到题目提供的同名项目。它不是完整工程，不能单独编译。原始题目、第三方 JAR、node_modules、target、dist、数据库、密钥和完整项目均不在包内。

详细说明见 `BookStore_s4/ASSIGNMENT2.md`。打包清单由 `BookStore_s4/scripts/package_submission.py` 显式维护，可重复生成提交包。

## 快速运行

需要 Java 17 或 20、Maven、Node.js 22.12+、Python 3，以及支持工具调用的兼容模型服务。

```bash
cd BookStore_s4
cp .env.example .env   # 若已有 .env，不要覆盖
# 编辑 .env，设置 LLM_BASE_URL、LLM_MODEL、LLM_API_KEY
bash scripts/run-assignment2.sh
```

访问 `http://127.0.0.1:5173`，打开右下角的智能书店助手，输入：

> 帮我查一下 ISBN 为 12345 的书还有多少本，顺便看看别家卖多少钱。

启动脚本启用 `demo,assistant`，使用独立的 H2 内存数据库和模拟竞品 HTTP 服务，不依赖 MySQL、Ollama 或 Chroma。模型 API 仍是真实服务，必须正确配置。按 Ctrl+C 关闭本次启动的进程。

已配置好本机密钥的环境可直接执行启动命令，勿再次复制 `.env.example`。
