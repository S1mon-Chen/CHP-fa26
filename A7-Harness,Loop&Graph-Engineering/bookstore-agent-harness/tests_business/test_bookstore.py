from decimal import Decimal
import pytest
from bookstore import calculate_total


def cart(price, quantity=1):
    return [{"price": price, "quantity": quantity}]


@pytest.mark.parametrize("items,coupon,expected", [
    (cart("20", 2), None, "50.00"),
    (cart("101"), None, "101.00"),
    (cart("100"), None, "100.00"),
    (cart("100"), "SAVE10", "100.00"),
    (cart("200"), "SAVE10", "180.00"),
    ([], None, "0.00"), ([], "SAVE10", "0.00"),
    (cart("0"), None, "10.00"),
    (cart("99.995"), None, "100.00"),
    (cart("1.005"), None, "11.01"),
    (cart("0.335", 3), None, "11.01"),
    (cart(Decimal("111.111")), "SAVE10", "100.00"),
    (cart(2, 3), None, "16.00"),
])
def test_totals(items, coupon, expected):
    result = calculate_total(items, coupon)
    assert isinstance(result, Decimal)
    assert result.as_tuple().exponent == -2
    assert result == Decimal(expected)


@pytest.mark.parametrize("quantity", [0, -1, True, 1.5, "1", None])
def test_bad_quantity(quantity):
    with pytest.raises(ValueError):
        calculate_total(cart("1", quantity))


@pytest.mark.parametrize("price", ["-1", "NaN", "Infinity", "-Infinity", "abc", 0.1, True, None])
def test_bad_price(price):
    with pytest.raises(ValueError):
        calculate_total(cart(price))


@pytest.mark.parametrize("items", [[], cart("20")])
def test_bad_coupon(items):
    with pytest.raises(ValueError):
        calculate_total(items, "OTHER")
