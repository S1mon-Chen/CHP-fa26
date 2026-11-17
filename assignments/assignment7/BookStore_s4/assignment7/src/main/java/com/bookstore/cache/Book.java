package com.bookstore.cache;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record Book(long id, String title, String author, BigDecimal price, int stock) {
    public record Write(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 100) String author,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stock) { }
}
