package com.bookstore.assignment4;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@RestController
public class AuthController {
    private final UserDetailsService users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final String dummyHash;

    public AuthController(UserDetailsService users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        dummyHash = passwords.encode("dummy-user-password");
    }

    public record LoginRequest(@NotBlank @Size(max = 80) String username,
                               @NotBlank @Size(max = 72) String password) { }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) { }

    @PostMapping("/api/auth/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能超过 72 字节");
        }
        UserDetails user = null;
        try {
            user = users.loadUserByUsername(request.username());
        } catch (UsernameNotFoundException ignored) {
            // Run a BCrypt check for unknown users as well.
        }
        boolean matches = passwords.matches(request.password(), user == null ? dummyHash : user.getPassword());
        if (user == null || !matches || !user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(new TokenResponse(jwt.issue(user.getUsername()), "Bearer", jwt.ttlSeconds()));
    }
}
