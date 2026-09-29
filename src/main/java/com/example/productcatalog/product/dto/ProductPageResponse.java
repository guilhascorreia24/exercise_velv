package com.example.productcatalog.product.dto;

import com.example.productcatalog.product.Product;
import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPageResponse(
        List<ProductResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static ProductPageResponse from(Page<Product> page) {
        return new ProductPageResponse(
                page.map(ProductResponse::from).getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
