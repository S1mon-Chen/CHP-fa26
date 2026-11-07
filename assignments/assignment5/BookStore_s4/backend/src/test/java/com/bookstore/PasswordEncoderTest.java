package com.bookstore;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {
    @Test void passwordHashesMatchOnlyTheOriginalPassword() {
        var encoder = new BCryptPasswordEncoder();
        String encoded = encoder.encode("test-password");
        assertThat(encoded).isNotEqualTo("test-password");
        assertThat(encoder.matches("test-password", encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }
}
