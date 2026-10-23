# 本轮实际验收

日期：2026-10-12；macOS，Python 3.12.7。新建独立 .venv，实际执行 `pip install -e ".[test]"` 成功。直接依赖 Pydantic 2.14.1，测试 pytest 8.3.5。既有 bookstore-agent-harness 文件未修改。

## 自动测试

`python -m pytest -q`：46 passed。

| 验收点 | 实际覆盖 |
|---|---|
| 原始行号、长段落不遗漏 | 重建整个文档、逐片段字符区间与行号比较、600 字符上限；含 TXT 和 CRLF |
| 相同快照、扫描顺序稳定 | 两次加载相同 ID；倒序创建另一目录仍相同 ID |
| 旧快照不变、重新加载变化 | 修改临时资料后检查旧对象不变、新 ID 改变；禁止修改 frozen 元数据和只读映射 |
| 越界、符号链接 | 越界文件链接、目录链接、内部链接全部拒绝 |
| 非法编码、空文件、大小、重复 ID | 各自失败且错误中无临时路径；不扫描外部目录，忽略非支持后缀 |
| 中文、英文、同义词 | 六组固定查询，检查命中词、分数贡献和 snippet 来自实际文本 |
| 无命中、过滤、top_k、排序 | 空白/停用词/不存在词均空结果；过滤与 top_k 生效；重复查询相同 |
| 未知工具、未知字段、严格范围 | 非法数字、字符串数字、布尔数字、长查询、ID 数量上限均拒绝 |
| 三工具相同权限 | list 不泄露文档；默认搜索限制可见项；显式越权搜索/读取拒绝；空权限集合为空 |
| 片段归属与路径攻击 | 不匹配、片段不存在、过多片段拒绝；../../、绝对路径、URL、命令作为 ID 均拒绝 |
| 仅读内存 | monkeypatch Path.open 禁止文件读取后，合法 search/read 仍成功 |
| 输出与 Schema | list/search/read 超限返回合法错误 JSON；参数 Schema 禁止未知字段并有 top_k 范围 |
| CLI 加载脱敏 | 缺失资料目录得到 CORPUS_LOAD_ERROR，不泄露路径 |

## 教学资料集实际 CLI 冒烟

`python tests/smoke_cli.py` 成功；记录见 smoke-results.json。

- 8 份文档、24 个片段，完整文本每份 760–865 Unicode 字符。短元数据节可能不足 300 字符；正文与尾段约 280–434 字符。
- `HDFS 副本`：命中 HDFS 文件块与副本。
- `Spark Stage Task`：命中 Spark 的 Job、Stage、Task。
- `Flink 水位线`：命中 Flink 事件时间与水印。
- `迟到消息`：命中 Flink 迟到数据与处理选择。
- `RowKey 多版本`：命中 HBase 的 RowKey、列族和多版本。
- `zzzznonexistent987`：hits=[]。
- 读取 HDFS 基础概念片段，覆盖原文第 9–14 行，哈希与搜索记录一致。
- 六组查询分别调用两次并比较完整 JSON；Schema 导出三工具；资料集文件前后字节不变。
- 额外通过已安装的 course-retrieval 命令执行文本搜索与完整读取；未知参数、路径尝试通过真实 CLI 返回 INVALID_ARGUMENTS。

## 范围与未验证项

没有实际部署 Hadoop/Spark/Flink/HBase，也没有执行其版本级运行或性能基准。资料只用于基础概念检索，官方来源记录在 sources.md。

本轮按范围没有实现/验证模型、网页或自动 Agent 循环。没有测试 Windows；当前验证环境为 macOS。未将单独 wheel 作为自带资料的安装包验证，README 使用教学源码目录的可编辑安装。

没有进行恶意本机用户并发替换资料目录的对抗实验；资料目录和配置应由可信教师维护。工具权限与快照测试不能证明检索到的段落一定足以回答问题，也不等于解决模型幻觉或 Prompt Injection。
