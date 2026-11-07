package com.bookstore.queue;

import com.bookstore.entity.*;
import com.bookstore.repository.*;
import com.bookstore.security.JwtService;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:order_queue_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "app.orders.processing-delay-ms=1500", "app.books.delay-ms=0"
})
@ActiveProfiles({"assignment5", "test"})
@EmbeddedKafka(partitions = 3, topics = "order-events", kraft = true,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderQueueHttpTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired OrderRepository orders;
    @Autowired OrderTaskRepository tasks;
    @Autowired OrderTaskProcessor processor;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    String aliceToken, bobToken;

    @BeforeAll void seed() {
        aliceToken = jwt.issue(user("queue-alice").getUsername());
        bobToken = jwt.issue(user("queue-bob").getUsername());
    }
    @AfterAll void close() { client.close(); }
    User user(String name) {
        User user = new User(); user.setUsername(name); user.setPassword("unused-test-password");
        user.setEmail(name + "@test.invalid"); user.setRole(User.Role.CUSTOMER); user.setStatus(User.Status.ACTIVE);
        return users.saveAndFlush(user);
    }
    Book book(int stock) {
        Book book = new Book(); book.setTitle("Queue test " + UUID.randomUUID()); book.setAuthor("test");
        book.setPrice(new BigDecimal("19.80")); book.setStock(stock); return books.saveAndFlush(book);
    }
    HttpRequest.Builder request(String path, String token) {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
        if (token != null) req.header("Authorization", "Bearer " + token);
        return req;
    }
    HttpResponse<String> post(String body, String token) throws Exception {
        return client.send(request("/api/orders", token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    String body(Book book, int quantity) {
        return "{\"items\":[{\"bookId\":" + book.getId() + ",\"quantity\":" + quantity + "}]}";
    }
    String accepted(Book book, int quantity) throws Exception {
        var response = post(body(book, quantity), aliceToken);
        assertThat(response.statusCode()).isEqualTo(202);
        var data = json.readTree(response.body());
        assertThat(data.path("status").asText()).isEqualTo("QUEUED");
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/status");
        return data.path("queueId").asText();
    }
    HttpResponse<String> stream(String id, String token) throws Exception {
        return client.send(request("/api/orders/" + id + "/stream", token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode terminal(String raw) throws Exception {
        JsonNode last = null;
        for (String line : raw.split("\n")) if (line.startsWith("data:")) last = json.readTree(line.substring(5));
        assertThat(last).isNotNull(); return last;
    }

    @Test void returns202BeforeSlowConsumerAndStreamsCommittedSuccess() throws Exception {
        Book book = book(4);
        long started = System.nanoTime();
        String id = accepted(book, 2);
        long acceptedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertThat(acceptedMs).isLessThan(1500);
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(4);
        var response = stream(id, aliceToken);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElseThrow()).contains("text/event-stream");
        assertThat(response.body()).contains("event:order-status", "QUEUED", "SUCCEEDED");
        var result = terminal(response.body());
        assertThat(orders.findById(result.path("orderId").asLong())).isPresent();
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(2);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isGreaterThanOrEqualTo(1500);
    }

    @Test void insufficientStockRollsBackWholeCartAndStreamsFailure() throws Exception {
        Book first = book(3), second = book(0);
        var response = post("{\"items\":[{\"bookId\":"+first.getId()+",\"quantity\":1},{\"bookId\":"+second.getId()+",\"quantity\":1}]}", aliceToken);
        assertThat(response.statusCode()).isEqualTo(202);
        String id = json.readTree(response.body()).path("queueId").asText();
        var result = terminal(stream(id, aliceToken).body());
        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        assertThat(result.path("message").asText()).contains("库存不足");
        assertThat(result.path("orderId").isNull()).isTrue();
        assertThat(books.findById(first.getId()).orElseThrow().getStock()).isEqualTo(3);
    }

    @Test void lateSubscriberReceivesStoredTerminalResult() throws Exception {
        String id = accepted(book(2), 1);
        JsonNode original = terminal(stream(id, aliceToken).body());
        var replay = stream(id, aliceToken);
        assertThat(terminal(replay.body())).isEqualTo(original);
        assertThat(replay.body()).doesNotContain("QUEUED");
    }

    @Test void duplicateKafkaDeliveryDoesNotDeductInventoryTwice() throws Exception {
        Book book = book(5); String id = accepted(book, 1);
        var original = terminal(stream(id, aliceToken).body());
        long count = orders.count();
        kafka.send("order-events", id, tasks.findById(id).orElseThrow().eventJson).get(5, TimeUnit.SECONDS);
        // The processor is also exercised directly, so this assertion cannot pass merely because redelivery is slow.
        assertThat(processor.process(id).orderId()).isEqualTo(original.path("orderId").asLong());
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(4);
        assertThat(orders.count()).isEqualTo(count);
    }

    @Test void competingOrdersCannotOversellLastCopy() throws Exception {
        Book book = book(1);
        String first = accepted(book, 1), second = accepted(book, 1);
        var results = List.of(terminal(stream(first, aliceToken).body()).path("status").asText(),
                terminal(stream(second, aliceToken).body()).path("status").asText());
        assertThat(results).containsExactlyInAnyOrder("SUCCEEDED", "FAILED");
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isZero();
    }

    @Test void streamsAndSnapshotsRejectOtherUsersAndAnonymousRequests() throws Exception {
        String id = accepted(book(1), 1);
        assertThat(stream(id, null).statusCode()).isEqualTo(401);
        assertThat(stream(id, bobToken).statusCode()).isEqualTo(403);
        var status = client.send(request("/api/orders/"+id+"/status", bobToken).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(status.statusCode()).isEqualTo(403);
        stream(id, aliceToken);
    }

    @Test void invalidInputIsRejectedBeforeEnqueueing() throws Exception {
        long count = tasks.count();
        for (String body : List.of("{\"items\":[]}", "{\"items\":[null]}", "{\"items\":[{\"bookId\":1,\"quantity\":0}]}", "{")) {
            assertThat(post(body, aliceToken).statusCode()).isEqualTo(400);
        }
        assertThat(post("{\"items\":[]}", null).statusCode()).isEqualTo(401);
        assertThat(tasks.count()).isEqualTo(count);
    }

    @Test void missingTaskIs404() throws Exception {
        assertThat(stream(UUID.randomUUID().toString(), aliceToken).statusCode()).isEqualTo(404);
    }
}
