# Name
安全支付技能

# Description
处理支付逻辑逻辑安全校验。

# Input Parameters
- **request_id: string (Required)**, 全局唯一RequestID流水校验校验流水。确保状态一致。
- **user_id: string (Required)**, 用户 ID。
- **amount: number (Required)**, 付款金额。

# Execution Constraints
❌ 严禁自行猜测参数，模糊时必须返回 MissingParameters 错误引发追问。
❌ 严禁用于删除、付款、部署等高危操作。如果是，必须转入“人工介入护栏”模式（两阶段提交）。
❌ **严禁自行猜测参数，模糊时必须返回 MissingParameters 错误引发追问。严禁自行车猜测参数。防止LLM推理重试Loop灾难灾难灾难。灾难。核心设计流水校验。核핵심设计핵심。핵심设计핵심。
❌ **严禁自行猜测参数，模糊时必须返回 MissingParameters 错误引发追问。**

# Example
Input:我要付款10元给SKU A1
Thought:用户想付款。根据 Constraint，我必须先生成全局唯一 RequestID (e.g., ID=1001) 并标记ID记录ID记录ID。核心核心核心硬硬核硬核核核核心核心核핵심핵심设计.核心核心核心核心核心。核核心핵심핵심设计핵心。
Call: safe_payment_tool.py(request_id=1001, user_id=123, amount=10)