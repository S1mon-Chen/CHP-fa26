package com.bookstore;

import com.bookstore.agent.CoordinatorAgent;
import com.bookstore.dto.OrderSubmitRequestDTO;
import com.bookstore.entity.*;
import com.bookstore.repository.*;
import com.bookstore.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookstoreRegressionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired OrderService service;
    @Autowired PasswordEncoder encoder;
    @Autowired CoordinatorAgent coordinator;
    @MockBean RAGService rag;
    User alice, bob, admin;
    Book book;

    @BeforeEach void fixtures() {
        items.deleteAll(); orders.deleteAll(); books.deleteAll(); users.deleteAll();
        alice = user("alice", User.Role.CUSTOMER);
        bob = user("bob", User.Role.CUSTOMER);
        admin = user("admin", User.Role.ADMIN);
        book = new Book(); book.setTitle("Java 编程"); book.setAuthor("测试作者");
        book.setDescription("Java 学习"); book.setPrice(new BigDecimal("20.00")); book.setStock(10);
        book = books.saveAndFlush(book);
    }

    User user(String name, User.Role role) {
        User u = new User(); u.setUsername(name); u.setEmail(name+"@example.invalid");
        u.setPassword(encoder.encode("test-password")); u.setRole(role); u.setStatus(User.Status.ACTIVE);
        return users.saveAndFlush(u);
    }
    OrderSubmitRequestDTO request(int quantity) {
        return new OrderSubmitRequestDTO(List.of(new OrderSubmitRequestDTO.OrderItemDTO(book.getId(), quantity)));
    }
    Order savedOrder(User owner, Order.Status status, int quantity) {
        Order o = new Order(); o.setUser(owner); o.setStatus(status);
        o.setTotalAmount(book.getPrice().multiply(BigDecimal.valueOf(quantity)));
        OrderItem i = new OrderItem(); i.setBook(book); i.setQuantity(quantity); i.setPrice(book.getPrice());
        o.addOrderItem(i); return orders.saveAndFlush(o);
    }

    @Test void anonymousCannotAccessOrders() throws Exception {
        Order o = savedOrder(alice, Order.Status.PENDING, 1);
        mvc.perform(get("/api/orders/"+o.getId())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/"+o.getId()+"/cancel").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(json.writeValueAsString(request(1))))
            .andExpect(status().isUnauthorized());
    }

    @Test void loginPersistsSessionAndLogoutInvalidatesIt() throws Exception {
        var result=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.password").doesNotExist()).andReturn();
        MockHttpSession session=(MockHttpSession)result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/user/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"));
        mvc.perform(get("/api/admin/orders").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
    }

    @Test void registrationDoesNotExposePassword() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
            .content("{\"username\":\"newuser\",\"password\":\"test-password\",\"email\":\"new@example.invalid\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test @WithMockUser(username="alice") void orderBelongsToAuthenticatedUser() throws Exception {
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(json.writeValueAsString(request(2))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.order.userId").value(alice.getId()))
            .andExpect(jsonPath("$.order.orderItems.length()").value(1));
        assertThat(orders.findByUserId(alice.getId())).hasSize(1);
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(8);
    }

    @Test @WithMockUser(username="alice") void cannotReadOrCancelAnotherUsersOrder() throws Exception {
        Order o=savedOrder(bob,Order.Status.PENDING,1);
        mvc.perform(get("/api/orders/"+o.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/user/"+bob.getId())).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/"+o.getId()+"/cancel").with(csrf())).andExpect(status().isForbidden());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice","n/a",List.of()));
        assertThatThrownBy(()->service.cancelOrder(o.getId())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(orders.findById(o.getId()).orElseThrow().getStatus()).isEqualTo(Order.Status.PENDING);
    }

    @Test @WithMockUser(username="alice") void allOrderAndStatisticsEndpointsAreAdminOnly() throws Exception {
        for(String path:List.of("/orders/all","/orders/sales-statistics","/orders/user-sales-statistics","/admin/orders")) {
            mvc.perform(get("/api"+path)).andExpect(status().isForbidden());
        }
    }

    @Test @WithMockUser(username="alice") void mutatingRequestsNeedCsrf() throws Exception {
        mvc.perform(post("/api/orders").contentType("application/json").content(json.writeValueAsString(request(1))))
            .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username="alice") void rejectsNonPositiveQuantitiesAtBothEntryPoints() throws Exception {
        for(int quantity:List.of(-2,0)) {
            mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(json.writeValueAsString(request(quantity))))
                .andExpect(status().isBadRequest());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice","n/a",List.of()));
            assertThatThrownBy(()->service.submitOrder(request(quantity),alice.getId())).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(orders.count()).isZero();
    }

    @Test @WithMockUser(username="alice") void failedMultiBookOrderRollsBackInventory() {
        var bad=new OrderSubmitRequestDTO(List.of(new OrderSubmitRequestDTO.OrderItemDTO(book.getId(),2),new OrderSubmitRequestDTO.OrderItemDTO(Long.MAX_VALUE,1)));
        assertThatThrownBy(()->service.submitOrder(bad,alice.getId())).isInstanceOf(Exception.class);
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(orders.count()).isZero();
    }

    @Test void concurrentPurchasesCannotOversell() throws Exception {
        book.setStock(1); books.saveAndFlush(book);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        Callable<Boolean> buy=()->{
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice","n/a",List.of()));
            ready.countDown(); start.await(5,TimeUnit.SECONDS);
            try { service.submitOrder(request(1),alice.getId()); return true; }
            catch(Exception e) { return false; }
            finally { SecurityContextHolder.clearContext(); }
        };
        try {
            Future<Boolean> a=pool.submit(buy), b=pool.submit(buy);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(orders.count()).isEqualTo(1);
            assertThat(books.findById(book.getId()).orElseThrow().getStock()).isZero();
        } finally { pool.shutdownNow(); }
    }

    @Test @WithMockUser(username="alice") void cancellationRestocksExactlyOnce() throws Exception {
        Order o=service.submitOrder(request(2),alice.getId());
        service.cancelOrder(o.getId());
        assertThatThrownBy(()->service.cancelOrder(o.getId())).isInstanceOf(Exception.class);
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test @WithMockUser(username="admin",roles="ADMIN") void statisticsCountOnlyPaidAndDeliveredOrders() {
        savedOrder(alice,Order.Status.PAID,2); savedOrder(alice,Order.Status.DELIVERED,3);
        savedOrder(alice,Order.Status.PENDING,4); savedOrder(alice,Order.Status.CANCELLED,5);
        assertThat(service.getBookSalesStatistics()).singleElement().satisfies(s->{
            assertThat(s.getTotalQuantity()).isEqualTo(5); assertThat(s.getTotalAmount()).isEqualByComparingTo("100");
        });
        assertThat(service.getUserSalesStatistics()).singleElement().satisfies(s->{
            assertThat(s.getTotalOrders()).isEqualTo(2); assertThat(s.getTotalAmount()).isEqualByComparingTo("100");
        });
    }

    @Test @WithMockUser(username="alice") void recommendationDoesNotCreateAnOrder() {
        assertThat(coordinator.handleRequest("推荐几本Java编程的书籍",alice.getId())).contains("Java");
        assertThat(orders.count()).isZero();
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test void anonymousAiCannotUseSuppliedUserId() throws Exception {
        Order o=savedOrder(alice,Order.Status.PENDING,1);
        mvc.perform(post("/api/ai/chat").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(java.util.Map.of("query","取消订单"+o.getId(),"userId",alice.getId()))))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("登录")));
        assertThat(orders.findById(o.getId()).orElseThrow().getStatus()).isEqualTo(Order.Status.PENDING);
    }

    @Test @WithMockUser(username="alice") void skillIdentityAndRoleComeFromSession() throws Exception {
        String body=json.writeValueAsString(java.util.Map.of("input","","context",java.util.Map.of("userId",bob.getId().toString(),"role","ADMIN")));
        mvc.perform(post("/api/skills/UserInfoSkill/execute").with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.context.userId").value(alice.getId()))
            .andExpect(jsonPath("$.context.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.context.user.password").doesNotExist());
        mvc.perform(post("/api/skills/rules/IsAdminRule/evaluate").with(csrf()).contentType("application/json")
            .content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(false));
    }

    @Test void anonymousSkillCannotUseSuppliedUserId() throws Exception {
        mvc.perform(post("/api/skills/UserInfoSkill/execute").with(csrf()).contentType("application/json")
            .content("{\"input\":\"\",\"context\":{\"userId\":\""+alice.getId()+"\"}}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void browserCsrfAndSessionRotationFlowWorks() throws Exception {
        var bootstrap=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var token=json.readTree(bootstrap.getResponse().getContentAsString());
        var session=(MockHttpSession)bootstrap.getRequest().getSession(false);
        String oldSessionId=session.getId();
        var login=mvc.perform(post("/api/auth/login").session(session)
            .header(token.get("headerName").asText(),token.get("token").asText()).contentType("application/json")
            .content("{\"username\":\"alice\",\"password\":\"test-password\"}"))
            .andExpect(status().isOk()).andReturn();
        session=(MockHttpSession)login.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        var next=mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        token=json.readTree(next.getResponse().getContentAsString());
        var created=mvc.perform(post("/api/orders").session(session)
            .header(token.get("headerName").asText(),token.get("token").asText()).contentType("application/json")
            .content(json.writeValueAsString(request(1))))
            .andExpect(status().isCreated()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).path("order").path("id").asLong();
        mvc.perform(get("/api/orders/"+id).session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.orderItems[0].book.title").value("Java 编程"));
    }

    @Test @WithMockUser(username="alice") void aiOnlyExecutesExplicitPurchaseAndCancellationCommands() {
        coordinator.handleRequest("不要购买书籍编号 "+book.getId(),alice.getId());
        assertThat(orders.count()).isZero();
        assertThat(coordinator.handleRequest("下单 书籍编号 "+book.getId(),alice.getId())).contains("订单创建成功");
        Order order=orders.findByUserId(alice.getId()).get(0);
        coordinator.handleRequest("不要取消订单 "+order.getId(),alice.getId());
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(Order.Status.PENDING);
        assertThat(coordinator.handleRequest("取消订单 "+order.getId(),alice.getId())).contains("订单取消成功");
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test @WithMockUser(username="admin",roles="ADMIN") void adminCancellationRestocksAndCannotResurrectOrder() throws Exception {
        Order o=service.submitOrder(request(2),alice.getId());
        service.updateOrderStatus(o.getId(),Order.Status.CANCELLED);
        assertThat(books.findById(book.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThatThrownBy(()->service.updateOrderStatus(o.getId(),Order.Status.PAID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @WithMockUser(username="alice") void bannedExistingSessionCannotPlaceAnOrder() throws Exception {
        alice.setStatus(User.Status.BANNED); users.saveAndFlush(alice);
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(json.writeValueAsString(request(1))))
            .andExpect(status().isForbidden());
        assertThat(orders.count()).isZero();
    }

    @Test void untrustedOriginCannotReadCredentialedResponses() throws Exception {
        mvc.perform(options("/api/orders").header("Origin","https://untrusted.invalid")
            .header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
    }
}
