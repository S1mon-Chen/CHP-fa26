# 任务：修复书店运费边界
只允许完整替换 `bookstore.py`，不得修改测试、Harness 或本说明。
实现 `calculate_total(items, coupon=None)`。
items 是由字典组成的列表，每项含 price 和 quantity。quantity 必须是正整数（拒绝 bool）；price 接受十进制字符串、整数、Decimal，拒绝 float、bool、非有限数及负数。非法值抛 ValueError。输入容器结构及字段完整性由调用者保证。
coupon 只接受 None 或 SAVE10，其他值抛 ValueError，包括空购物车的非法优惠券。
先用 Decimal 精确累加商品金额，SAVE10 使商品金额乘 0.9，然后对折后商品金额一次性按 ROUND_HALF_UP 四舍五入到两位小数。以这个已舍入金额判断运费：大于等于 100 元免运费，否则运费 10 元。空购物车返回 Decimal('0.00')，不收运费。返回值始终为两位小数的 Decimal。
初始代码仅含一个 Bug：将满 100 免运费写为超过 100 免运费。
响应必须是 JSON 对象，且恰好具有 path 和 code 两个字符串字段：
{"path": "bookstore.py", "code": "完整 Python 源码"}
不得返回 Markdown 代码围栏、命令或其他字段。测试结果由 Harness 决定。
