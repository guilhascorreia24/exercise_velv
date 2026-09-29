package com.example.productcatalog.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Partial, case-insensitive match on the product name ({@code LOWER(name) LIKE %term%}).
     * LIKE wildcards in {@code name} are escaped by Spring Data.
     */
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Deletes in a single statement and returns the number of deleted rows (0 or 1).
     * Unlike find-then-delete, two concurrent deletes of the same id cannot both "find" the row:
     * the second one simply deletes 0 rows.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Product p where p.id = :id")
    int deleteProductById(@Param("id") Long id);
}
