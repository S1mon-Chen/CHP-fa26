# 教学示例来源核对

核对日期：2026-10-12。corpus 内全部为独立编写的中文教学示例，不是官方文档译文。仅参考下列官方来源的基础概念，课堂练习与权限说明是本项目设计。没有编造默认参数或性能结果。

| 示例 | 官方来源与核对内容 |
|---|---|
| hdfs_basics | [Hadoop 2.10.2 HDFS Architecture](https://hadoop.apache.org/docs/r2.10.2/hadoop-project-dist/hadoop-hdfs/HdfsDesign.html)：文件块、副本、NameNode 与 DataNode 的职责。未沿用版本默认数值。 |
| mapreduce_basics | [Hadoop 3.4.0 MapReduce Tutorial](https://hadoop.apache.org/docs/r3.4.0/hadoop-mapreduce-client/hadoop-mapreduce-client-core/MapReduceTutorial.html)：Map 键值对、按键组织与 Reduce 聚合。 |
| spark_execution | [Spark 3.5.8 Cluster Mode Overview](https://spark.apache.org/docs/3.5.8/cluster-overview.html)：Glossary 中 Job、Stage、Task 定义。 |
| spark_streaming | [Spark 3.5.8 Structured Streaming Guide](https://spark.apache.org/docs/3.5.8/structured-streaming-programming-guide.html)：事件时间、水印与状态清理，不将特定示例推广为所有算子保证。 |
| flink_event_time | [Flink Timely Stream Processing](https://nightlies.apache.org/flink/flink-docs-stable/docs/concepts/time/)：事件时间进度与水印。 |
| flink_late_data | [Flink Windows](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/operators/windows/)：Allowed Lateness 与迟到处理选择。不引用默认值。 |
| hbase_basics | [HBase Reference Guide](https://hbase.apache.org/book.html)：行键、列族和带时间戳的版本。 |
| course_scope | 本资料集自身覆盖范围，不依赖外部版本结论。 |

Flink stable 与 HBase 在线指南是可变链接，不冒充固定版本存档。因为此轮只采用基础表述，没有断言具体发行版的 API 签名、默认行为或性能；后续补充版本级资料时需要固定版本及再次核查。
