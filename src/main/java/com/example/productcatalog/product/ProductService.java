package com.example.productcatalog.product;

import com.example.productcatalog.exception.ProductNotFoundException;
import com.example.productcatalog.product.dto.CreateProductRequest;
import com.example.productcatalog.product.dto.ProductPageResponse;
import com.example.productcatalog.product.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Returns one page of products, optionally filtered by name.
     * Out-of-range paging values are normalised (1 <= size <= {@value #MAX_PAGE_SIZE}, page >= 0)
     * so a single request can never load the whole catalog. The page is also capped so that the
     * row offset (page * size) fits in an int, which is the limit JPA accepts.
     */
    public ProductPageResponse findProducts(String search, int page, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageNumber = Math.clamp(page, 0, Integer.MAX_VALUE / pageSize);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, DEFAULT_SORT);

        Page<Product> result = (search == null || search.isBlank())
                ? productRepository.findAll(pageable)
                : productRepository.findByNameContainingIgnoreCase(search.trim(), pageable);

        return ProductPageResponse.from(result);
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = new Product(request.name().trim(), request.category().trim(), request.price().doubleValue());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        if (productRepository.deleteProductById(id) == 0) {
            throw new ProductNotFoundException(id);
        }
    }
}
