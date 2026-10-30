package org.reins.se3353.se3353_3_websockettransfer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
public class BankController {
    @Autowired
    private BankService bankService;

    @GetMapping(value = "/transfer")
    public String transfer() {
        System.out.println("Transferring...");
        bankService.transfer("Tom", "Jerry", 80);
        return "Done";
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @RequestMapping("/send")
    public void send() {
        String data = "Tom,Jerry,80";
        kafkaTemplate.send("topic1",  "key", data);
        System.out.println(data);
    }
}
