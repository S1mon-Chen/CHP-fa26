package org.reins.se3353.se3353_7_msgredis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;

@RestController
public class MsgController {
    @Autowired
    WebApplicationContext applicationContext;

    @GetMapping(value = "/msg")
    public void findOne( ) {
        StringRedisTemplate template = applicationContext.getBean(StringRedisTemplate.class);

        // Send a message with a POJO - the template reuse the message converter
        System.out.println("Sending an email message.");
        template.convertAndSend("chat", "Hello from Redis in HTTP!");
    };
}