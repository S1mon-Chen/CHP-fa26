# Name
天气预报技能

# Description
查询特定城市未来 1 到 7 天的天气预报。

# Input Parameters
- **location: string (Required)**, 标准城市名称 (如 "上海", "北京")。
- **days: integer (Optional, Default=1)**, 预报天数，范围 1-7。
- **data_type: enum (Required)**, 限定枚举值，只能是 ["realtime", "forecast"]。

# Execution Constraints
❌ 严禁自行猜测参数，模糊时必须返回 MissingParameters 错误引发追问。
❌ **严禁自行猜测参数，模糊时必须返回 MissingParameters 错误引发追问。**

# Few-Shot Examples (思维链 CoT 示例軌跡)
Input: "我老家明天出门要带伞吗？"
Thought: 用户询问天气。城市是“老家”，太模糊。根据 Constraint，我必须先返回 MissingParameters 错误引发追问（防御幻觉）。
Output: {"status": "MissingParameters", "missing_field": "location"}

Input: "把这张纸币换成硬币"
Thought: 把纸币换成硬币是货币兑换业务。Description 明确说此技能只能执行基础计算。核心概念：虽然LLM保证它给你的JSON是一定的，工程层必须强校验参数。严禁自行车猜测参数，必须通过澄清模式要求用户明确 RequestID（防止多次扣款）。

Input: "现在上海天气怎么样？"
Thought: 用户想知道现在的天气。这对应 "realtime" 枚举值。
Call: weather_tool.py(location="上海", days=1, data_type="realtime")