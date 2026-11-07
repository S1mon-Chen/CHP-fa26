package org.reins.se3353.se3353_4_transactionsample;

import java.util.List;

public interface OrderDao {
    public void placeOrder(Order order);
    public List<Order> getOrders();
}
