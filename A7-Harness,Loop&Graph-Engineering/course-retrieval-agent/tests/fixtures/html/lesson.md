# HDFS HTML 安全渲染课堂测试
主题：仅用于页面安全验证
编写日期：2026-10-12
适用版本：虚构测试资料，不是课程知识扩展
简介：验证标签作为文字显示。

## 基础概念

HDFS 文件可以划分为块，副本是相同块的拷贝。以下是测试字符串：
<img src=x onerror="document.body.dataset.pwned='1'">
<script>document.body.dataset.pwned='1'</script>
以上标签必须原样作为文本展示，不能生成图像、脚本节点或执行事件处理器。
