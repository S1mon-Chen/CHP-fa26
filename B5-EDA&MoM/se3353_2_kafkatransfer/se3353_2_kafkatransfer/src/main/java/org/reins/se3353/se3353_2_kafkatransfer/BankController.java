package org.reins.se3353.se3353_2_kafkatransfer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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
