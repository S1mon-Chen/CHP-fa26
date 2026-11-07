package org.reins.se3353.se3353_4_transactionsample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private OrderItemDao orderItemDao;

    @Override
    @Transactional
    public void placeOrder(Order order){
        System.out.println("Order Service" + order.getId());
        Set<OrderItem> orderItems = order.getOrderitems();

        Order orderIn = new Order();
        orderIn.setId(order.getId());

        orderDao.placeOrder(orderIn);

        System.out.println("Order Saved");

//        int result = 10 / 0;
        try {
            orderItemDao.placeOrderItem(orderItems);
            System.out.println("Order Items Saved");

        }catch (Exception e){
            e.printStackTrace();
        }
//        int result = 10 / 0;
    };

}
