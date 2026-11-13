# 作业 6 Seata AT 分布式下单

学号：524031910668　姓名：陈熹朗

已实现三个独立 Java 服务、两套 MySQL 业务库，以及真实 Apache Seata AT 分布式事务。正常下单同时扣库存和生成订单；订单 INSERT 后抛出 RuntimeException 时，Seata 自动恢复已经提交的库存。

- 提交：`524031910668-作业6.zip`，仅含本次源码、配置、脚本、文档和证据。
- [实现与复现说明](BookStore_s4/assignment6/README.md)
- [实验报告](作业6实验报告.docx)
- [28 项真实验收结果](BookStore_s4/assignment6/evidence/verification.txt)
- [正常与异常的浏览器截图](BookStore_s4/assignment6/evidence/03-automatic-rollback.png)

## 启动

需要已启动的 Docker、JDK 21、Maven 3.9+ 和 Python 3。

```bash
cd BookStore_s4
export JAVA_HOME=/你的/JDK21/目录
bash start.sh
```

打开 <http://127.0.0.1:18080/>。另一个终端执行：

```bash
cd BookStore_s4/assignment6
python3 scripts/verify.py
```

本机现有临时 JDK 可用路径为 `/tmp/assignment5-jdk21/Contents/Home`；其他机器请替换为自己的 JDK 21。解压提交包后的命令相同。首次启动需要下载 Maven 依赖和 Docker 镜像。

作业 6 的实现位于 `BookStore_s4/assignment6`，使用专用数据库和端口。复制来的旧作业工程不是提交内容；运行本作业不需要 Kafka、React、原书店 MySQL 库或模型密钥。
