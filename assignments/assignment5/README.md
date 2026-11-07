# 作业 5 Kafka 异步订单与 SSE 推送

学号：524031910668　姓名：陈熹朗

作业已整合到 `BookStore_s4`。提交订单返回 **202 和排队 ID**，Kafka 消费者完成库存扣减后，通过 **SSE 推送成功或失败**。完整书店购物车与独立演示页均可运行。

- [实现说明与评分点对应](BookStore_s4/ASSIGNMENT5.md)
- [成功与失败的完整截图](BookStore_s4/evidence-kafka/03-success-and-failure.png)
- [完整书店排队截图](BookStore_s4/evidence-kafka/04-bookstore-queued.png)
- [测试与证据](BookStore_s4/evidence-kafka/test-summary.json)
- 提交文件：`524031910668-作业5.zip`

## 启动

需要已启动的 Docker、JDK 21、Maven、Python 3；完整书店前端还需要 Node.js/npm。

```bash
cd assignments/assignment5/BookStore_s4
export JAVA_HOME=/你的/JDK21/路径
bash start.sh
```

演示页：<http://127.0.0.1:8080/orders-demo.html>。完整书店：<http://127.0.0.1:5173>。

顾客账号：`student` / `bookstore-demo-2026`。使用隔离 H2 数据库，重启后演示订单清空。Kafka 为 Apache 官方 3.9.1 镜像的单节点 KRaft。

本机验证安装的临时 JDK 位于 `/tmp/assignment5-jdk21/Contents/Home`，仍存在时可将上述 `JAVA_HOME` 设为这个路径。

解压提交包后，进入包内 `BookStore_s4`，运行同一个 `bash start.sh`，会自动采用精简构建，不需要整个原工程。压缩包不含第三方 JAR、依赖、密钥或模型文件。
