# 第一轮保留的接入接口说明

第二轮已实现原生 Ollama 适配与 Harness，参见 [当前架构](agent-architecture.md)。下文记录第一轮设计边界；接口仍兼容。

```python
from app.corpus import load_corpus
from app.tools import ToolService, export_schemas

snapshot = load_corpus('corpus')
service = ToolService(snapshot, allowed_document_ids=None)
schemas = export_schemas()
result = service.invoke_tool('search_documents', {'query': 'Flink 水位线', 'top_k': 3})
```

统一入口是绑定了快照和权限的 `service.invoke_tool(name, arguments)`。arguments 必须为 JSON 对象解析后的 dict，不能是待执行的 Python 表达式。导出 schema 是 `{name, parameters}` 列表；下一轮可以将它适配成模型服务的工具声明，但模型无权传入配置路径或扩大 allowed_document_ids。

一个请求会话应固定持有一个 ToolService，避免搜索与读取之间静默切换快照。重新加载应创建新 service，并使上层明确知道快照已变；旧引用不能不经检查直接当作新资料引用。模型上下文只需要工具 JSON 和必要原文，不需要本机路径。

成功调用不代表已经回答问题。下轮应要求回答引用快照、文档、片段和内容哈希；空结果或内容不足时明确说明。资料文本中出现的指令仍属于被检索内容，不是修改工具白名单的授权。

本轮没有实现 Ollama 请求、自动循环、重试预算或答案生成，也没有用模拟模型冒充真实调用。未来接入时仍需要独立设计这些控制。
