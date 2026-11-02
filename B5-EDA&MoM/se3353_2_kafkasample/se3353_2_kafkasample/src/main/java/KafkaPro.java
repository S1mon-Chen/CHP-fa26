import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class KafkaPro {
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.setProperty("transactional.id", "my-transactional-id");

        Producer<String, String> producer = null;

        try {
            producer = new KafkaProducer<String, String>(props, new StringSerializer(), new StringSerializer());
            producer.initTransactions();

            producer.beginTransaction();
            for (int i = 0; i < 100; i++)
                producer.send(new ProducerRecord<String, String>("quickstart-events", Integer.toString(i), "Message " + Integer.toString(i)));
            producer.commitTransaction();
        } catch (ProducerFencedException e) {
            producer.close();
        } catch (OutOfOrderSequenceException e) {
            producer.close();
        } catch (AuthorizationException e) {
            producer.close();
        } catch (KafkaException e) {
            producer.abortTransaction();
        }
        producer.close();
    }
}
