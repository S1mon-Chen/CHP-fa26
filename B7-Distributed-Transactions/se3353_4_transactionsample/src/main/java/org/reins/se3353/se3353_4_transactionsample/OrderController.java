package org.reins.se3353.se3353_4_transactionsample;

import org.aspectj.weaver.ast.Or;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OrderController {
    @Autowired
    private OrderDao orderDao;

    @Autowired
    OrderService orderService;

    @GetMapping(value = "/order")
    public String placeOrder(@RequestBody Order newOrder) {
        System.out.println("Placing Order...");
        orderService.placeOrder(newOrder);
        return "Place Order Done";
    }

    @GetMapping(value = "/orderdetail")
    public List<Order> getOrder( ) {
        System.out.println("Getting Orders...");
        return orderDao.getOrders();
    }
}
