package org.reins.se3353.se3353_4_transactionsample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class OrderDaoImpl implements OrderDao {
    @Autowired
    private OrderRepository orderRepository;

    @Override
    @Transactional//(propagation = Propagation.REQUIRES_NEW)
    public void placeOrder(Order order) {
        orderRepository.save(order);
    }

    @Override
    public List<Order> getOrders(){
        List<Order> orders = orderRepository.findAll();
        Order order = orders.get(0);
        System.out.println(order.getOrderitems().size());
        return orderRepository.findAll();
    }
}
