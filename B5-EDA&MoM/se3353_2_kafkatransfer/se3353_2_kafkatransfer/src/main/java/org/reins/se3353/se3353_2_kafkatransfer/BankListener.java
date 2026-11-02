package org.reins.se3353.se3353_2_kafkatransfer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BankListener {
    @Autowired
    private BankService bankService;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "topic1", groupId = "group_topic_test")
    public void topic1Listener(ConsumerRecord<String, String> record) {
        String[] value = record.value().split(",");
        bankService.transfer(value[0], value[1], Integer.valueOf(value[2]));
        kafkaTemplate.send("topic2",  "key", "Done");
    }

    @KafkaListener(topics = "topic2", groupId = "group_topic_test")
    public void topic2Listener(ConsumerRecord<String, String> record) {
        String value = record.value();
        System.out.println(value);
    }
}
