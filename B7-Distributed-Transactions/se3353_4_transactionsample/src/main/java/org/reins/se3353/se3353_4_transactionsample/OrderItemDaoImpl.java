package org.reins.se3353.se3353_4_transactionsample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Repository
public class OrderItemDaoImpl implements OrderItemDao {
    @Autowired
    private OrderItemRepository orderItemRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void placeOrderItem(Set<OrderItem> orderItems) {
        for (OrderItem orderItem : orderItems) {
            orderItemRepository.save(orderItem);
        }
//        int result = 10 / 0;
    }
}
