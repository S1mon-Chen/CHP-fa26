package com.bookstore.dto;

import com.bookstore.entity.User;
import java.time.LocalDateTime;

public record UserResponseDTO(Long id, String username, String email, User.Role role,
                              User.Status status, LocalDateTime createdAt) {
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
