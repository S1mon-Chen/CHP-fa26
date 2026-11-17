package com.bookstore.tx.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = {"com.bookstore.tx.checkout", "com.bookstore.tx.common"})
public class CheckoutApplication {
    public static void main(String[] args) { SpringApplication.run(CheckoutApplication.class, args); }
}
