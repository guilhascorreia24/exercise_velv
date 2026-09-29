# Product Catalog

A Spring Boot application with a simple frontend for searching, paginating, adding and removing products.
It starts with 1,500 preloaded products in an in-memory H2 database, so it has no external dependencies.

- **Backend:** Java 21, Spring Boot 3.5 (Web, Data JPA, Validation), H2 in-memory
- **Frontend:** vanilla HTML/CSS/JS, served by Spring Boot from `src/main/resources/static/`
- **Tests:** JUnit 5, Mockito, MockMvc

## Requirements

- JDK 21
- Maven 3.9+

## Run

```bash
mvn spring-boot:run
```

Then open:

| URL | What |
|-----|------|
| http://localhost:8080 | Frontend (search, pagination, add, delete) |
| http://localhost:8080/api/products | REST API |
| http://localhost:8080/h2-console | H2 console (JDBC URL `jdbc:h2:mem:catalog`, user `sa`, empty password) |

The database is in-memory (`create-drop`), so every restart begins with the same 1,500 products.

### Build a runnable jar

```bash
mvn clean package
java -jar target/product-catalog-0.0.1-SNAPSHOT.jar
```

## Test

```bash
mvn test                                  # all tests
mvn test -Dtest=ProductControllerTest     # MockMvc tests against the seeded H2 database
mvn test -Dtest=ProductServiceTest        # Mockito unit tests for the service
```

## API

| Method | Path | Success | Errors |
|--------|------|---------|--------|
| `GET` | `/api/products?page=0&size=20&search=` | `200` `ProductPageResponse` | `400` for non-numeric `page` / `size` |
| `POST` | `/api/products` | `201` `ProductResponse` | `400` validation failed / malformed JSON |
| `DELETE` | `/api/products/{id}` | `204` | `404` product not found |

**Paging rules:** `page` defaults to `0` and `size` to `20`. `size` is capped at `100` and raised to at least `1`, and a negative `page` becomes `0`.
The response `size` field shows the size that was actually used. Results are sorted by `id` ascending.
`search` does a partial, case-insensitive match on the product name. Blank means no filter.

### Examples

```bash
# first page (20 items)
curl "http://localhost:8080/api/products"

# page 3, 50 per page
curl "http://localhost:8080/api/products?page=2&size=50"

# search by name (case-insensitive, partial)
curl "http://localhost:8080/api/products?search=head"

# create
curl -i -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Mouse","category":"Technology","price":50.00}'

# delete
curl -i -X DELETE http://localhost:8080/api/products/1
```

`GET` response:

```json
{
  "items": [
    { "id": 1, "name": "Classic Headphones Lite", "category": "Electronics", "price": 66.9 }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1500,
  "totalPages": 75
}
```

Error response (every error uses the same shape; `errors` appears only for validation failures):

```json
{
  "timestamp": "2026-09-26T18:41:12.452Z",
  "status": 400,
  "message": "Validation failed",
  "errors": {
    "category": "Category is required",
    "name": "Name is required",
    "price": "Price must be greater than 0"
  }
}
```

## Project structure

```
src/main/java/com/example/productcatalog/
├── ProductCatalogApplication.java
├── product/        # entity, repository, service, controller
│   └── dto/        # CreateProductRequest, ProductResponse, ProductPageResponse
├── exception/      # ProductNotFoundException, ApiError, GlobalExceptionHandler
└── config/         # DataInitializer (1,500 seed products)
src/main/resources/
├── application.yml
└── static/         # index.html, app.js, styles.css
src/test/java/com/example/productcatalog/
├── ProductCatalogApplicationTest.java
└── product/        # ProductControllerTest, ProductServiceTest
```

## Documentation

- [docs/ai/architecture.md](docs/ai/architecture.md): architecture and design decisions
- [docs/ai/implementation.md](docs/ai/implementation.md): implementation notes, decisions and AI usage
- [docs/ai/frontend-prototype.md](docs/ai/frontend-prototype.md): how the original frontend prototype was generated
