package com.example.productcatalog.product;

import com.example.productcatalog.exception.ProductNotFoundException;
import com.example.productcatalog.product.dto.CreateProductRequest;
import com.example.productcatalog.product.dto.ProductPageResponse;
import com.example.productcatalog.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void findProductsWithoutSearchReturnsPageSortedById() {
        Product headphones = productWithId(1L, "Wireless Headphones", "Electronics", 79.99);
        when(productRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> pageOf(invocation.getArgument(0), 1500, headphones));

        ProductPageResponse response = productService.findProducts(null, 0, 20);

        verify(productRepository).findAll(PageRequest.of(0, 20, Sort.by("id").ascending()));
        verify(productRepository, never()).findByNameContainingIgnoreCase(anyString(), any());
        assertThat(response.items()).containsExactly(new ProductResponse(1L, "Wireless Headphones", "Electronics", 79.99));
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1500);
        assertThat(response.totalPages()).isEqualTo(75);
    }

    @Test
    void findProductsWithSearchUsesTrimmedCaseInsensitiveNameFilter() {
        Product mouse = productWithId(7L, "Gaming Mouse", "Electronics", 49.90);
        when(productRepository.findByNameContainingIgnoreCase(eq("mouse"), any(Pageable.class)))
                .thenAnswer(invocation -> pageOf(invocation.getArgument(1), 1, mouse));

        ProductPageResponse response = productService.findProducts("  mouse ", 0, 20);

        verify(productRepository, never()).findAll(any(Pageable.class));
        assertThat(response.items()).extracting(ProductResponse::name).containsExactly("Gaming Mouse");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void findProductsBlankSearchIsTreatedAsNoFilter() {
        when(productRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> pageOf(invocation.getArgument(0), 0));

        productService.findProducts("   ", 0, 20);

        verify(productRepository, never()).findByNameContainingIgnoreCase(anyString(), any());
    }

    @Test
    void findProductsNormalisesOutOfRangePaging() {
        when(productRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> pageOf(invocation.getArgument(0), 0));

        ProductPageResponse response = productService.findProducts(null, -3, 5_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(ProductService.MAX_PAGE_SIZE);
        assertThat(response.size()).isEqualTo(ProductService.MAX_PAGE_SIZE);
    }

    @Test
    void createPersistsTrimmedProductAndReturnsResponse() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        ProductResponse response = productService.create(new CreateProductRequest("  Mouse ", " Technology ", 50.0));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Mouse");
        assertThat(captor.getValue().getCategory()).isEqualTo("Technology");
        assertThat(captor.getValue().getPrice()).isEqualTo(50.0);
        assertThat(response).isEqualTo(new ProductResponse(42L, "Mouse", "Technology", 50.0));
    }

    @Test
    void deleteRemovesExistingProduct() {
        Product product = productWithId(5L, "Desk Lamp", "Home", 29.99);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        productService.delete(5L);

        verify(productRepository).delete(product);
    }

    @Test
    void deleteUnknownProductThrowsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product with id 99 not found");

        verify(productRepository, never()).delete(any(Product.class));
    }

    private static Product productWithId(Long id, String name, String category, double price) {
        Product product = new Product(name, category, price);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private static Page<Product> pageOf(Pageable pageable, long total, Product... products) {
        return new PageImpl<>(List.of(products), pageable, total);
    }
}
