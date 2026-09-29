package com.example.productcatalog.product.dto;

import com.example.productcatalog.product.Product;

public record ProductResponse(Long id, String name, String category, double price) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getCategory(), product.getPrice());
    }
}
