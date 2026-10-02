# 电商金融级退款智能编排体 (Commerce Agent Orchestrator)

这是一个基于 **本地 Ollama (Qwen2.5) 驱动** 的金融级退款智能编排系统。系统实现了自主反欺诈空间安全审计、硬约束政策风控守护（Hard-Guardrails），以及具备子工具超时故障自愈、容错自适应重试（Fail-Fast & Idempotent Retry）的异步事件流式编排链路。

---

## 🌟 核心能力说明

1. **RAG 优化路由与合规审计**：加载退款政策规范约束（如 Premium 级别的硬限额门槛）。
2. **空间一致性校验与动态澄清**：利用本地 Ollama 实时对异地定位的交易风险生成优雅、自然的安全追问语进行动态澄清。
3. **故障隔离与智能重试机制**：子工具（如第三方退款单接口）发生 Timeout 错误时，通过 Ollama 进行智能的异常模式归类与决策。若评估为可恢复错误（如 `GATEWAY_TIMEOUT`），则基于同一全局幂等键（Idempotency Key）发起安全重试。
4. **实时异步事件生成器**：使用 Python `asyncio` 与 Async Generator，实时向前端推送编排状态机事件、思考路径（CoT）及最终输出的时序 DAG 追踪图。

---

## 📂 项目目录结构

```text
largeskill/
├── app.py                      # 基于 Streamlit 的交互式 Web 仪表盘
├── start.sh                    # 仪表盘后台启动脚本（支持自动记录 PID）
├── stop.sh                     # 仪表盘关闭脚本（自动清理进程与记录）
├── streamlit.log               # 仪表盘后台运行日志（启动后自动生成）
├── README.md                   # 本文档
└── .agent/
    └── skills/
        └── commerce_refund_skill/
            ├── core/
            │   └── agent_orchestrator.py   # 编排核心逻辑（实现 execute_workflow_stream）
            ├── prompts/
            │   └── agent_templates.py      # LLM 决策模板（RAG、GPS 澄清、异常决策）
            └── tools/
                └── sub_tools.py            # 金融级 API 仿真工具（内置网络延迟和幂等机制）
```

---

## 🛠️ 环境准备与先决条件

### 1. 本地 Ollama 配置
确保本地已经安装并运行 [Ollama](https://ollama.com/)，且本地已拉取 `qwen2.5:latest` 模型：
```bash
# 验证 Ollama 服务是否就绪及模型是否存在
ollama list
```
*注：如果在模型列表中发现没有 `qwen2.5:latest`，请执行 `ollama pull qwen2.5:latest`。*

### 2. Python 依赖库
项目要求 Python 3.9+，核心运行仅依赖标准库。交互式看板运行需安装 `streamlit`：
```bash
pip install streamlit
```

---

## 🚀 运行与操作指南

### 选项 A：命令行运行（CLI 模式）
你可以直接在终端中以事件流模式运行 Agent：
```bash
PYTHONPATH=.agent/skills python -m commerce_refund_skill.core.agent_orchestrator
```

### 选项 B：启动交互式 Web 仪表盘（网页模式 - 推荐）

网页控制台提供了滑块调节金额、自定义 GPS 地理偏离、开关网络超时等动态控制，并伴有**发光脉冲状态灯**和**时序 DAG Trace 树**展示。

1. **一键启动控制台**：
   ```bash
   ./start.sh
   ```
2. **访问仪表盘**：
   在浏览器中访问：**[http://localhost:8501](http://localhost:8501)**。
3. **关闭控制台**：
   ```bash
   ./stop.sh
   ```
