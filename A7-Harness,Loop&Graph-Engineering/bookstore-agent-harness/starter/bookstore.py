"""书店金额计算；教学初始版本只有免运费边界这一处缺陷。"""
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP


def calculate_total(items, coupon=None):
    if coupon not in (None, "SAVE10"):
        raise ValueError("unknown coupon")
    subtotal = Decimal("0")
    for item in items:
        quantity = item["quantity"]
        if type(quantity) is not int or quantity <= 0:
            raise ValueError("quantity must be a positive integer")
        price = item["price"]
        if isinstance(price, bool) or not isinstance(price, (str, int, Decimal)):
            raise ValueError("price must be a decimal string, integer or Decimal")
        try:
            price = Decimal(price)
        except (InvalidOperation, ValueError):
            raise ValueError("invalid price") from None
        if not price.is_finite() or price < 0:
            raise ValueError("price must be finite and nonnegative")
        subtotal += price * quantity
    if not items:
        return Decimal("0.00")
    if coupon == "SAVE10":
        subtotal *= Decimal("0.9")
    subtotal = subtotal.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    shipping = Decimal("0") if subtotal > Decimal("100") else Decimal("10")
    return subtotal + shipping
