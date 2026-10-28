package com.bookstore.assignment4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.auth.username=student", "app.auth.password=test-password", "app.books.delay-ms=25"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookstoreHttpTest {
    private static final SecretKey KEY = Jwts.SIG.HS256.key().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(KEY.getEncoded()));
    }

    @AfterAll
    void close() {
        client.close();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        return send(request("/api/auth/login").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(
                        new AuthController.LoginRequest(username, password)))));
    }

    private String token() throws Exception {
        var login = login("student", "test-password");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie")).isEmpty();
        return mapper.readTree(login.body()).get("accessToken").asText();
    }

    private HttpResponse<String> book(String token) throws Exception {
        return send(request("/api/books/1").header("Authorization", "Bearer " + token).GET());
    }

    private void assertUnauthorized(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(response.body()).get("status").asInt()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void missingTokenIs401() throws Exception {
        assertUnauthorized(send(request("/api/books/1").GET()));
    }

    @Test
    void loginIssuesSignedExpiringJwt() throws Exception {
        var response = login("student", "test-password");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(900);
        var claims = Jwts.parser().verifyWith(KEY).build()
                .parseSignedClaims(body.get("accessToken").asText()).getPayload();
        assertThat(claims.getSubject()).isEqualTo("student");
        assertThat(claims.getIssuer()).isEqualTo("bookstore-assignment4");
        assertThat(claims.getAudience()).contains("bookstore-api");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(900_000L);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void validTokenReturnsBookOnActualVirtualRequestThread() throws Exception {
        var response = book(token());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).get("id").asLong()).isEqualTo(1);
        assertThat(response.headers().firstValue("X-Request-Virtual")).contains("true");
        assertThat(response.headers().firstValue("X-Request-Thread")).hasValueSatisfying(
                name -> assertThat(name).isNotBlank());
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        assertThat(login("student", "wrong").statusCode()).isEqualTo(401);
    }

    @Test
    void unknownUserIs401() throws Exception {
        assertThat(login("nobody", "test-password").statusCode()).isEqualTo(401);
    }

    @Test
    void blankLoginIs400() throws Exception {
        assertThat(login("", "").statusCode()).isEqualTo(400);
    }

    @Test
    void oversizedUtf8PasswordIs400() throws Exception {
        assertThat(login("student", "密".repeat(25)).statusCode()).isEqualTo(400);
    }

    @Test
    void invalidJsonIs400RatherThanAuthenticationError() throws Exception {
        var response = send(request("/api/auth/login").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{")));
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer invalid", "Bearer ", "Basic abc", "Bearer a.b.c", "Token abc"})
    void malformedAuthorizationIs401(String header) throws Exception {
        assertUnauthorized(send(request("/api/books/1").header("Authorization", header).GET()));
    }

    @Test
    void duplicateAuthorizationIs401() throws Exception {
        String token = token();
        assertUnauthorized(send(request("/api/books/1").header("Authorization", "Bearer " + token)
                .header("Authorization", "Bearer " + token).GET()));
    }

    @Test
    void tamperedSignatureIs401() throws Exception {
        String token = token();
        int signature = token.lastIndexOf('.') + 1;
        String modified = token.substring(0, signature) + (token.charAt(signature) == 'A' ? 'B' : 'A')
                + token.substring(signature + 1);
        assertUnauthorized(book(modified));
    }

    private String signed(String issuer, String audience, Instant expiration, Instant notBefore, String subject) {
        return Jwts.builder().subject(subject).issuer(issuer).audience().add(audience).and()
                .expiration(expiration == null ? null : Date.from(expiration))
                .notBefore(notBefore == null ? null : Date.from(notBefore))
                .signWith(KEY, Jwts.SIG.HS256).compact();
    }

    @Test
    void expiredTokenIs401() throws Exception {
        assertUnauthorized(book(signed("bookstore-assignment4", "bookstore-api",
                Instant.now().minusSeconds(60), null, "student")));
    }

    @Test
    void futureTokenIs401() throws Exception {
        assertUnauthorized(book(signed("bookstore-assignment4", "bookstore-api",
                Instant.now().plusSeconds(120), Instant.now().plusSeconds(60), "student")));
    }

    @Test
    void missingExpirationIs401() throws Exception {
        assertUnauthorized(book(signed("bookstore-assignment4", "bookstore-api", null, null, "student")));
    }

    @Test
    void missingSubjectIs401() throws Exception {
        assertUnauthorized(book(signed("bookstore-assignment4", "bookstore-api",
                Instant.now().plusSeconds(60), null, null)));
    }

    @Test
    void wrongIssuerIs401() throws Exception {
        assertUnauthorized(book(signed("another-app", "bookstore-api", Instant.now().plusSeconds(60), null, "student")));
    }

    @Test
    void wrongAudienceIs401() throws Exception {
        assertUnauthorized(book(signed("bookstore-assignment4", "another-api", Instant.now().plusSeconds(60), null, "student")));
    }

    @Test
    void unsignedTokenIs401() throws Exception {
        String unsigned = Jwts.builder().subject("student").issuer("bookstore-assignment4")
                .audience().add("bookstore-api").and().expiration(Date.from(Instant.now().plusSeconds(60)))
                .compact();
        assertUnauthorized(book(unsigned));
    }

    @Test
    void loginDoesNotAuthorizeSubsequentRequestsWithoutToken() throws Exception {
        assertThat(book(token()).statusCode()).isEqualTo(200);
        assertUnauthorized(send(request("/api/books/1").GET()));
        assertUnauthorized(send(request("/api/books/1").header("Cookie", "JSESSIONID=pretend-session").GET()));
    }

    @Test
    void bearerSchemeIsCaseInsensitive() throws Exception {
        assertThat(send(request("/api/books/1").header("Authorization", "bearer " + token()).GET())
                .statusCode()).isEqualTo(200);
    }

    @Test
    void unknownBookIs404() throws Exception {
        assertThat(send(request("/api/books/999").header("Authorization", "Bearer " + token()).GET())
                .statusCode()).isEqualTo(404);
    }

    @Test
    void invalidBookIdIs400() throws Exception {
        assertThat(send(request("/api/books/0").header("Authorization", "Bearer " + token()).GET())
                .statusCode()).isEqualTo(400);
    }

    @Test
    void bookCollectionIsRestResource() throws Exception {
        var response = send(request("/api/books").header("Authorization", "Bearer " + token()).GET());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).size()).isEqualTo(3);
    }

    @Test
    void concurrentRequestsAllUseVirtualThreadsAndReturn200() throws Exception {
        String token = token();
        List<CompletableFuture<HttpResponse<String>>> futures = IntStream.range(0, 40)
                .mapToObj(i -> client.sendAsync(request("/api/books/1")
                        .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString()))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        for (var future : futures) {
            assertThat(future.join().statusCode()).isEqualTo(200);
            assertThat(future.join().headers().firstValue("X-Request-Virtual")).contains("true");
            assertThat(future.join().headers().allValues("Set-Cookie")).isEmpty();
        }
    }
}
