# 作业 7 Redis 缓存与 Sentinel 限流

学号：524031910668　姓名：陈熹朗

已完成图书详情 Cache-Aside、空对象缓存防穿透、Sentinel 5 QPS 限流，以及真实 MySQL/Redis 并发验收。28 项检查通过；50 线程短突发得到 5 个 HTTP 200、45 个 HTTP 429，数据库查询增量 0。

- 提交包：`524031910668-作业7.zip`，仅收录本次源码、配置、脚本、文档与证据。
- [实现和复现说明](BookStore_s4/assignment7/README.md)
- [实验报告](作业7实验报告.docx)
- [验收结果](BookStore_s4/assignment7/evidence/verification.txt)
- [429 压测截图](BookStore_s4/assignment7/evidence/01-http429-loadtest.png)

## 运行

需要 Docker、JDK 17+、Maven 3.9+、Python 3：

```bash
cd BookStore_s4
bash start.sh
```

打开 <http://127.0.0.1:18087/>。另一个终端执行：

```bash
cd BookStore_s4/assignment7
python3 scripts/verify.py --outage
```

验证与页面压测请分别运行，避免共用的 5 QPS 额度相互影响。本次实现选择题目允许的“防穿透”方案；一致性与单机限流边界见详细说明。复制来的其他作业工程不参与运行，也不包含在提交包中。
