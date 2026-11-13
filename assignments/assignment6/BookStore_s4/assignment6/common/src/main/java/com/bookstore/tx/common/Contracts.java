package com.bookstore.tx.common;

import jakarta.validation.constraints.*;

public final class Contracts {
    private Contracts() {}

    public record PlaceOrder(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String requestId,
            @NotNull @Positive Long bookId,
            @Min(1) @Max(100000) int quantity,
            boolean failOrder,
            @Min(0) @Max(3000) int pauseAfterInventoryMs) {}

    public record Deduction(long bookId, String title, int quantity, int beforeStock, int afterStock, String xid) {}
    public record OrderResult(long orderId, String requestId, long bookId, int quantity, String xid, boolean replayed) {}
    public record OrderLookup(boolean found, OrderResult order) {}
    public record Book(long bookId, String title, int stock) {}
    public record ApiError(String code, String message, String xid, String requestId) {}
}
