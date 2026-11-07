package org.reins.se3353.se3353_4_transactionsample;

import java.util.Set;

public interface OrderItemDao {
    public void placeOrderItem(Set<OrderItem> orderItems);
}
