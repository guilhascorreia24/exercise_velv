package com.example.productcatalog.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Partial, case-insensitive match on the product name ({@code LOWER(name) LIKE %term%}).
     * LIKE wildcards in {@code name} are escaped by Spring Data.
     */
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
