package com.example.productcatalog.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @NotBlank(message = "Category is required")
        @Size(max = 255, message = "Category must be at most 255 characters")
        String category,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be greater than 0")
        Double price
) {
}
