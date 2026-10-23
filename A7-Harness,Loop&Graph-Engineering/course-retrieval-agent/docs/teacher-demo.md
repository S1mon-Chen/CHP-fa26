# 十分钟教师演示

课前：依 README 安装 Python 3.12 环境；运行 pytest 和 smoke 脚本。全程不需要 Docker、Ollama 或网络服务。

1. **0–2 分钟，加载资料。** 打开 corpus/hdfs_basics.md，展示标题、主题、日期、适用范围。运行 `python -m app.cli check` 与 `python -m app.cli snapshot`。提问：为什么文件时间不该决定快照 ID？为什么每个 CLI 进程加载一次，而工具调用不反复开文件？
2. **2–4 分钟，解释命中。** 搜索 `HDFS 副本`、`Spark Stage Task`。观察检索词、term_scores 与片段。搜索 `Flink 水位线`、`迟到消息`，展示显式同义词扩展。问：一个片段只命中标题，能否据此直接回答？
3. **4–6 分钟，证据定位。** 搜索 `RowKey 多版本`，复制 document_id 和 chunk_id 调用 read；比较源文件行号和片段文本。使用 JSON 查看 content_hash 与 corpus_snapshot_id。若一个长段落占一行，被切成两块，为什么两个片段行号可以相同？
4. **6–8 分钟，权限和错误。** 运行 README 的 invoke 非法字段与路径示例。复制 config.json 为课堂临时配置，将 allowed_document_ids 改成 list 输出中的一个 ID，演示三个工具遵守同一范围。不要修改示例原资料，也不要把未授权错误解释成文件不存在。
5. **8–10 分钟，资料不足。** 搜索 `zzzznonexistent987`，观察空结果。再搜索某个版本性能问题，即使检索到“资料不足”的段落，也不等于得到性能数字。最后导出 schema，让学生找到 top_k 的范围和 additionalProperties=false。

可选课后实验：在临时目录复制两份资料，先保留 snapshot 对象，再改源文件并重新加载。对比旧对象、新快照和 chunk_id。对应测试为 test_snapshot_stability_immutability_reload；不要直接改用户工作区资料。

课堂要区分：加载错误（编码/大小/链接）、调用错误（未知字段/非法范围）、权限拒绝、合法的无命中。检索成功是工具完成查询，不能当成答案正确。
