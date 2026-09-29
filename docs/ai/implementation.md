# AI Usage: Backend Implementation & Frontend Integration

## Summary

- **Purpose:** Implement the Product Catalog backend described in [architecture.md](architecture.md) and connect the
  existing frontend prototype to the real REST API.
- **AI agent used:** Claude (Claude Code, Opus 5.5), acting as a Senior Java / Spring Boot engineer.
- **Prompt used:** Implement the application from `architecture.md`, `frontend-prototype.md` and the prototype files
  (`app.js`, `index.html`, `styles.css`) against these acceptance criteria:
  Java 21 + `mvn spring-boot:run`, H2 at `jdbc:h2:mem:catalog`, exactly 1,500 seeded products,
  paginated/searchable `GET /api/products` (default `page=0`, `size=20`, `size <= 100`),
  validated `POST` (201), `DELETE` (204/404), a `GlobalExceptionHandler` with a standard JSON error payload,
  `ProductControllerTest` + `ProductServiceTest`, static files that call the API with `fetch()`,
  and updated `README.md` / `implementation.md`.

## What was generated

| Area | Files |
|------|-------|
| Build / config | `pom.xml` (Spring Boot 3.5.1, Java 21), `src/main/resources/application.yml`, `.gitignore` |
| Entry point | `ProductCatalogApplication.java` |
| Domain | `product/Product.java`, `ProductRepository.java`, `ProductService.java`, `ProductController.java` |
| DTOs | `product/dto/CreateProductRequest.java`, `ProductResponse.java`, `ProductPageResponse.java` |
| Errors | `exception/ProductNotFoundException.java`, `ApiError.java`, `GlobalExceptionHandler.java` |
| Seed data | `config/DataInitializer.java` |
| Frontend | `frontend/*` moved to `src/main/resources/static/`. `app.js` rewritten to use `fetch()` |
| Tests | `ProductControllerTest`, `ProductServiceTest`, `ProductCatalogApplicationTest` |
| Docs | `README.md`, this file. `architeture.md` renamed to `architecture.md` to match the target structure |

## Acceptance criteria → implementation

| # | Criterion | Where / how |
|---|-----------|-------------|
| 1 | Runs with `mvn spring-boot:run`, Java 21 | `pom.xml`: `java.version=21`, `spring-boot-maven-plugin` |
| 1 | H2 in memory `jdbc:h2:mem:catalog` | `application.yml` (`ddl-auto: create-drop`) |
| 2 | Exactly 1,500 products | `DataInitializer`: 20 adjectives × 3 editions × 25 items = 1,500 unique names. Verified by `ProductCatalogApplicationTest` |
| 3 | Paginated GET, `size <= 100`, case-insensitive search, `ProductPageResponse` | `ProductController.list` → `ProductService.findProducts` → `findAll(Pageable)` / `findByNameContainingIgnoreCase` |
| 3 | POST with `@Valid`, 201 + `ProductResponse` | `ProductController.create`, `CreateProductRequest` constraints |
| 3 | DELETE 204, 404 via `ProductNotFoundException` | `ProductService.delete` (`findById().orElseThrow`) |
| 4 | Standard error JSON for 404 / 400 | `GlobalExceptionHandler` + `ApiError` |
| 5 | Controller and service tests | see [Testing](#testing) |
| 6 | Static frontend uses `fetch()` | `src/main/resources/static/app.js` |

## Key implementation decisions

### Layering

- **Controller** is thin: it binds parameters, runs `@Valid` and sets status codes (`@ResponseStatus(CREATED)`,
  `@ResponseStatus(NO_CONTENT)`).
- **Service** owns the rules: paging normalisation, trimming input, the not-found case, and transactions
  (`@Transactional(readOnly = true)` by default, with writes overriding it).
- **DTOs** are Java records. The JPA entity is never serialised directly. `ProductResponse.from(Product)` and
  `ProductPageResponse.from(Page<Product>)` handle the mapping, so no mapping library is needed.

### Pagination and the 100-item cap

- The controller takes plain `page` / `size` ints (defaults `0` / `20`), not a `Pageable` argument. The service
  builds the `PageRequest`, so the limit and sort live in one place.
- Out-of-range values are **clamped, not rejected**: `size` goes to `[1, 100]` and `page` to `>= 0`. A client asking
  for `size=1500` gets 100 items and `"size": 100`, so the endpoint can never return the whole catalog. This follows
  the architecture note ("protection for high size value") and still forgives a bad client.
- Sorting is fixed to `id ASC`, as the architecture specifies.
- A non-numeric `page`/`size` returns `400` with the standard error payload.

### Search

- Search is a partial, case-insensitive name match using `findByNameContainingIgnoreCase`. Spring Data escapes LIKE
  wildcards, so `%` and `_` in the search term are matched literally. The term is trimmed, and a blank term means
  "no filter".

### Validation and errors

- `CreateProductRequest`: `name` and `category` are `@NotBlank` and `@Size(max = 255)`; `price` is `@NotNull` and
  `@Positive`. The `@Size` limit matches the default column length. Without it an over-long name would fail in the
  database and return a 500 instead of a 400.
- `price` is a boxed `Double` in the request so that a missing price gives "Price is required" instead of
  silently becoming `0`.
- `ApiError` payload: `{ timestamp, status, message, errors? }`. `errors` (field → message, sorted by field) is only
  included for validation failures. It is an addition to the architecture example so the UI can show which field
  is wrong.
- Handled: `ProductNotFoundException` → 404, `MethodArgumentNotValidException` → 400 "Validation failed",
  `HttpMessageNotReadableException` → 400 "Malformed request body", `MethodArgumentTypeMismatchException` → 400.
  There is deliberately no catch-all `Exception` handler, because it would turn framework 404/405 responses into 500s.

### Seed data

- The data is deterministic: names come from a cartesian product, and prices from a base price × edition factor ×
  a ±10 % variation drawn from `new Random(42)`, rounded to cents. Restarts and tests always see the same data.
  Item names include "Headphones" and "Headset", so the architecture's `head` search example works
  (120 matches).
- Seeding is skipped if the table already has rows.
- **`DataInitializer` implements `SmartInitializingSingleton`, not `ApplicationRunner`.** Manual testing found that
  `ApplicationRunner`s run *after* Tomcat has opened port 8080. For about 1 second after startup, the API answered
  with `totalElements: 0`. `afterSingletonsInstantiated()` runs after every bean is ready but before the embedded
  server starts, so the first request always sees 1,500 products.

### Frontend integration

- The mock functions (`mockGetProducts`, `mockAddProduct`, `mockDeleteProduct`) and the `localStorage` store were
  replaced by `getProducts`, `addProduct` and `deleteProduct`, built on one `request()` helper around `fetch()`.
- Errors: non-2xx responses are parsed as `ApiError`, and the UI shows `message` plus any field errors. Network
  failures show a "could not reach the server" message.
- The default page size changed from 5 to 20 to match the backend.
- **Bug fixed from the prototype:** with no `?size=` in the URL, `Number(null)` is `0`, so the page size became 1.
  The querystring is now parsed with `parseInt` and only applied when present.
- After a delete empties the last page, the UI steps back to the previous page.
- Small additions: `min="0.01"` on the price input, a `success` status style, and disabled styling for the paging
  buttons.

## Testing

Run with `mvn test` (20 tests, all passing at time of writing).

| Test class | Type | Covers |
|------------|------|--------|
| `ProductControllerTest` | `@SpringBootTest` + MockMvc, `@Transactional` (rolled back per test) | GET defaults (20 items, 1,500 total, 75 pages), requested page, size capped at 100, non-numeric page → 400, case-insensitive partial search (`HEAD` → 120), empty search result, POST 201, POST 400 with field errors, malformed JSON 400, DELETE 204, DELETE 404 payload |
| `ProductServiceTest` | Mockito / JUnit 5 | default paging and sort, search delegation and trimming, blank search, clamping of page/size, creation (trimmed + mapped), deletion, not-found exception |
| `ProductCatalogApplicationTest` | `@SpringBootTest` | context starts, exactly 1,500 products seeded, static frontend served |

Design notes:

- The controller tests run **full stack against the seeded H2 database**, not a `@WebMvcTest` slice with a mocked
  service. Pagination and search results are only meaningful against real data. The service's logic is still
  unit-tested in isolation in `ProductServiceTest`.
- Both Spring test classes use the same configuration (`@SpringBootTest @AutoConfigureMockMvc`), so they share one
  cached application context. Two different configurations would create two contexts pointing at the same
  `jdbc:h2:mem:catalog` database, and the second context's `create-drop` would wipe the first one's data.

## Verification performed

- `mvn clean test`: 20/20 passing.
- `mvn spring-boot:run` and manual `curl` checks: default page (20/1500/75), `size=1000` → 100, `search=head` → 120,
  POST valid → 201, POST invalid → 400 with field errors, DELETE → 204, repeated DELETE → 404, `/` serves the UI.

## What the developer should review or change

- **Architecture doc inconsistencies**, resolved as follows:
  - The POST example uses `catalog`, but the entity and acceptance criteria use `category`. The implementation uses `category`.
  - The Repository section says "search case sensitive", while the Search section and acceptance criteria say
    case-insensitive. The implementation is case-insensitive.
  - "1,000+ products": 1,500 are seeded, per the acceptance criteria.
- `price` is a `double`, as the architecture specifies. For real money, prefer `BigDecimal` / `NUMERIC(…, 2)` to avoid
  floating-point rounding.
- `ApiError.java` is one file beyond the target structure. It holds the shared error payload.
- The H2 console is enabled for local development. Disable it (`spring.h2.console.enabled: false`) in any shared
  environment.
- Test runs print a Mockito "self-attaching agent" warning on JDK 21+. It is harmless now. Configure Mockito as a
  `-javaagent` in Surefire before the JDK disallows dynamic agents.
- `docs/ai/review.md` from the target structure has not been written yet.
