package com.example.productcatalog.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * {@code price} is a {@link BigDecimal} (not {@code Double}) so the exact JSON value is validated:
 * a {@code Double} turns {@code 1e400} into {@code Infinity}, which {@code @Digits} cannot handle.
 */
public record CreateProductRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @NotBlank(message = "Category is required")
        @Size(max = 255, message = "Category must be at most 255 characters")
        String category,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be greater than 0")
        @DecimalMax(value = "10000.00", message = "Price must be less than or equal to 10000.00")
        @Digits(integer = 5, fraction = 2, message = "Price must have at most 5 digits in the integer part and 2 digits in the fractional part")
        BigDecimal price
) {
}
