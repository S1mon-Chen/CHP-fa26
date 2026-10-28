package com.bookstore.config;

import com.bookstore.entity.User;
import com.bookstore.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Public demo accounts, enabled only alongside the isolated assignment4 H2 profile. */
@Component
@Profile("assignment4")
public class Assignment4DemoData implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public Assignment4DemoData(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        create("student", "bookstore-demo-2026", User.Role.CUSTOMER);
        create("admin", "admin-demo-2026", User.Role.ADMIN);
    }

    private void create(String username, String password, User.Role role) {
        if (users.findByUsername(username).isPresent()) return;
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwords.encode(password));
        user.setEmail(username + "@assignment4.invalid");
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);
        users.save(user);
    }
}
