# 科研项目管理系统：Vibe Coding 零代码开发过程指南

针对科技管理人员，我们不需要手写框架代码。本指南将展示如何通过 **WorkBuddy Vibe Coding**（自然语言对话驱动），指挥 AI 一步步完成架构搭建与系统组装。

---

## 宏观开发阶段

基于大模型驱动的复杂系统开发范式（Vibe Coding）通常分为四个阶段：
1. **基础设施打底 (Infrastructure)**：建目录、切数据库双模、设环境变量。
2. **知识与工具武装 (RAG & Skills)**：构建大脑知识库与工具手脚。
3. **数字员工捏造 (Agents)**：定义多智能体拓扑关系。
4. **管线与护栏组装 (Pipeline & Harness)**：上安全锁，跑通端到端流程。

---

## 详细开发步骤与 Vibe 指令实操

打开 WorkBuddy，新建工作区 `research_system_lab`。在聊天框中依次发送以下“总指挥令”。

### 第一阶段：基础设施与双模数据库构建

> 💬 **指令 1：初始化架构与双模数据层**
> "请在当前工作区帮我初始化一个科研项目管理系统。
> 1. 创建 `.env` 文件，包含 `DEEPSEEK_API_KEY` 和 `DB_MODE=file`（支持 file/mysql 切换）。
> 2. 生成 `database_layer.py`：如果是 file 模式，使用 JSON 读写 `projects.json`；如果是 mysql 模式，使用 SQLAlchemy 连接 MySQL。请实现一个通用的 `save_project_application()` 方法和 `update_project_status()` 方法。"

**💡 管理者视角**：通过一句话就实现了底层存储隔离。白天上课用 `file` 模式演示，回单位把 `.env` 改成 `mysql` 就能直接连局域网数据库，架构弹性极强。

### 第二阶段：接入外部标准与计算插件 (MCP & Skill)

> 💬 **指令 2：添加预算合规检查 Skill**
> "请帮我编写一个能力插件 `budget_skill.py`。包含一个函数 `check_budget_compliance(budget_dict, total_amount)`。
> 规则：科研劳务费比例不能超过 20%，专家咨询费单项不能超过 5 万。
> 返回结果必须是确定性的 JSON：包含 `is_compliant` (布尔值) 和 `reason`。"

> 💬 **指令 3：引入 MCP 查新接口假数据**
> "请生成一个 `mcp_scholar_client.py`，模拟通过 MCP 协议调用外部学术论文库。输入项目关键字，返回 3 篇相关历史论文的标题和摘要，用于后续的查新比对。"

**💡 管理者视角**：我们明确告诉 AI，“算钱”这种事不能靠猜，必须用 Skill 里的硬代码算；查文献必须用 MCP 调标准接口。这就是工程化思维。

### 第三阶段：定义多智能体与 RAG 知识库 (Agents & RAG)

> 💬 **指令 4：构建多智能体审计拓扑**
> "请帮我创建 `multi_agent_reviewer.py`。
> 1. 建立基于本地目录 `./policies` 的 RAG 检索器，加载《科技厅科研项目管理办法》。
> 2. 定义【查新智能体】：调用 mcp_scholar_client，比对申报书内容与过往论文，判断创新性。
> 3. 定义【预算合规智能体】：调用 budget_skill.py，计算金额合规性。
> 4. 定义【主控 Supervisor】：协调前两者，结合 RAG 政策，输出综合《AI 初审报告》。"

### 第四阶段：套上 Harness 护栏与沙箱验证 (Capstone)

这是最核心的收官阶段：防止 AI 滥用职权，确保人类管理者的绝对控制。

> 💬 **指令 5：上装甲并执行端到端沙箱测试**
> "最后一步，请帮我生成总控脚本 `app_gateway.py` 与 Harness 配置文件 `.workbuddy/harness.json`。
> 1. Harness 规则：严禁大模型直接调用 update_project_status 批准立项。当 Supervisor 给出'建议通过'时，必须触发 HITL（人类参与）机制，终端暂停，提示'请科研处长输入 YES 确认立项'。
> 2. 请在当前工作区生成一份模拟申报书 `sample_application.json`，并立刻在沙箱中运行 `app_gateway.py`。我要看到它自动分发给各 Agent 审查，最后挂起等待我审批的完整过程日志！"

---

## 成果验收与复盘

当 WorkBuddy 在沙箱终端中跑完最后一条指令，科技管理人员将亲眼看到：
1. **AI 自动提取**了 `sample_application.json` 中的参数。
2. **预算 Skill** 精准报错：“劳务费占比 25%，超出 20% 规定”。
3. **查重 Agent** 发现与历史课题雷同。
4. 或者一切合规时，**Harness 网关** 稳稳地把流程拦截下来，等待人工敲下 `YES`。

通过这个过程，科技管理人员不仅学会了 AI 工具，更掌握了**“大模型能力下限靠 Agent，安全上限靠 Harness 与 Skill”**的核心管理哲学。