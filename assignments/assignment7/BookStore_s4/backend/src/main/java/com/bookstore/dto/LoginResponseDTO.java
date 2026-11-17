package com.bookstore.dto;

/** Keeps the existing user/message fields while adding the stateless access credential. */
public record LoginResponseDTO(String message, UserResponseDTO user, String accessToken,
                               String tokenType, long expiresIn) { }
