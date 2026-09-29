package com.example.productcatalog.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack tests (controller -> service -> repository -> H2) against the 1,500 seeded products.
 * Each test runs in a transaction that is rolled back, so the seed data stays intact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsFirstPageWithDefaultPaging() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1500))
                .andExpect(jsonPath("$.totalPages").value(75));
    }

    @Test
    void listReturnsRequestedPage() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[0].id").value(21))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void listCapsPageSizeAtOneHundred() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "1500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(100)))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalPages").value(15));
    }

    @Test
    void listRejectsNonNumericPaging() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'page'"));
    }

    @Test
    void searchIsPartialAndCaseInsensitive() throws Exception {
        // "HEAD" matches every "...Headphones..." and "...Headset..." product: 2 items x 20 adjectives x 3 editions
        mockMvc.perform(get("/api/products").param("search", "HEAD").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(120))
                .andExpect(jsonPath("$.items", hasSize(100)))
                .andExpect(jsonPath("$.items[*].name", everyItem(containsStringIgnoringCase("head"))));
    }

    @Test
    void searchWithoutMatchesReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/products").param("search", "no-such-product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void createReturns201WithCreatedProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Test Mouse", "category": "Technology", "price": 50.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Test Mouse"))
                .andExpect(jsonPath("$.category").value("Technology"))
                .andExpect(jsonPath("$.price").value(50.00));

        mockMvc.perform(get("/api/products").param("search", "test mouse"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createWithInvalidPayloadReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " ", "category": "", "price": -5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").value("Name is required"))
                .andExpect(jsonPath("$.errors.category").value("Category is required"))
                .andExpect(jsonPath("$.errors.price").value("Price must be greater than 0"));
    }

    @Test
    void createWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mouse\", \"price\": \"not-a-number\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void deleteExistingProductReturns204() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", 1))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/products"))
                .andExpect(jsonPath("$.totalElements").value(1499))
                .andExpect(jsonPath("$.items[0].id").value(2));
    }

    @Test
    void deleteUnknownProductReturns404() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product with id 999999 not found"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
