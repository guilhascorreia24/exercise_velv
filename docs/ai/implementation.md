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
| Build / config | `pom.xml` (Spring Boot 3.5.1, Java 21; Actuator added later by the developer), `src/main/resources/application.yml`, `.gitignore` |
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
| 3 | DELETE 204, 404 via `ProductNotFoundException` | `ProductService.delete` → `ProductRepository.deleteProductById` (0 rows → exception) |
| 4 | Standard error JSON for 404 / 400 (and every other error) | `GlobalExceptionHandler` (extends `ResponseEntityExceptionHandler`) + `ApiError` |
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
- `page` is also capped at `Integer.MAX_VALUE / size`, because JPA only accepts an `int` row offset. Without the cap,
  `?page=21474837&size=100` returned a 500. It now returns an empty page.
- Sorting is fixed to `id ASC`, as the architecture specifies.
- A non-numeric `page`/`size` returns `400` with the standard error payload.

### Search

- Search is a partial, case-insensitive name match using `findByNameContainingIgnoreCase`. Spring Data escapes LIKE
  wildcards, so `%` and `_` in the search term are matched literally. The term is trimmed, and a blank term means
  "no filter".

### Validation and errors

- `CreateProductRequest`: `name` and `category` are `@NotBlank` and `@Size(max = 255)`. The `@Size` limit matches
  the default column length; without it an over-long name would fail in the database and return a 500 instead of a
  400.
- `price` is `@NotNull`, `@Positive`, `@DecimalMax("10000.00")` and `@Digits(integer = 5, fraction = 2)`. The
  developer added the upper bound and scale after the review found that `1e400` was accepted and stored as `Infinity`.
- In the request, `price` is a `BigDecimal`, not a `Double`:
  - a missing price gives "Price is required" instead of silently becoming `0`;
  - the exact JSON value is validated. With a `Double`, Jackson turns `1e400` into `Infinity`, and Hibernate
    Validator's `@Digits` then crashes (`HV000028`), which becomes a 500. A test covers this case.

  The entity keeps `double`, as architecture.md specifies. The service converts with `doubleValue()`, which is exact
  for values with at most 5 integer and 2 decimal digits.
- `ApiError` payload: `{ timestamp, status, message, errors? }`. `errors` (field → message, sorted by field) is only
  included for validation failures. It is an addition to the architecture example so the UI can show which field
  is wrong.
- `GlobalExceptionHandler` extends Spring's `ResponseEntityExceptionHandler`:
  - Every standard Spring MVC exception keeps its correct status: 405 (with an `Allow` header), 415, 406, 404 for
    unknown static resources, 400 for bad parameters, and so on. The overridden `createResponseEntity` only replaces
    Spring's default `ProblemDetail` body with an `ApiError`.
  - Custom messages: `MethodArgumentNotValidException` → "Validation failed" + `errors`,
    `HttpMessageNotReadableException` → "Malformed request body", type mismatch →
    "Invalid value for parameter 'x'".
  - `ProductNotFoundException` → 404.
  - A catch-all `Exception` handler logs the error and returns 500 "Unexpected error" without internal details.
    It cannot swallow framework exceptions, because their more specific handlers in the base class win.
- One side effect: the error payload is JSON everywhere, so a browser opening an unknown URL gets a JSON 404
  instead of Spring's HTML "Whitelabel" page.

### Delete

- `ProductRepository.deleteProductById` runs a single `DELETE … WHERE id = :id` (`@Modifying @Query`) and returns
  the number of deleted rows. The service throws `ProductNotFoundException` when that number is 0.
- The first version used `findById()` then `delete()`. Under concurrency, two requests could both find the row, and
  the second then failed with an `ObjectOptimisticLockingFailureException` (a 500). With the single statement, the
  second request waits for the row lock, deletes 0 rows and returns 404.
- `flushAutomatically`/`clearAutomatically` keep the persistence context consistent with the bulk delete.

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
- Small additions: a `success` status style, disabled styling for the paging buttons, and input limits that match
  the backend: `min="0.01"`/`max="10000"` on price and `maxlength="255"` on name and category.
- Changes made after the final review (see [review.md](review.md)):
  - `load(targetPage)` only updates `page` when the server answers, and returns whether it succeeded. "Product
    added"/"deleted" is only shown if the reload worked; otherwise the reload error stays visible.
  - Each `load()` gets a request number, and responses from older requests are ignored, so fast clicking cannot
    show a stale page. Paging buttons are disabled while a request is in flight.
  - A new status message cancels the pending "hide" timer of an earlier success message.
  - Each input has a visually hidden `<label>` (`.sr-only`) for screen readers.

### Health check (Actuator)

- `spring-boot-starter-actuator` was added to `pom.xml` by the developer after the AI implementation, to provide a
  standard health check endpoint.
- It uses Spring Boot's default configuration; there is no `management.*` configuration in `application.yml`. By
  default, only the `health` endpoint is exposed over HTTP:
  - `GET /actuator/health` → `{"status":"UP"}`
  - `GET /actuator` → links to the exposed endpoints
- No application code depends on Actuator. Other endpoints (metrics, env, beans, ...) are not exposed over HTTP.
  They would have to be enabled explicitly with `management.endpoints.web.exposure.include`.
- Both endpoints were checked with `curl` during the final review (see [review.md](review.md)).

## Testing

Run with `mvn test` (31 tests, all passing at time of writing).

| Test class | Type | Covers |
|------------|------|--------|
| `ProductControllerTest` (21) | `@SpringBootTest` + MockMvc, `@Transactional` (rolled back per test) | GET defaults (20 items, 1,500 total, 75 pages), requested page, size capped at 100, huge page → empty page (not 500), non-numeric page → 400, case-insensitive partial search (`HEAD` → 120), search + pagination, empty search result, POST 201, POST at the maximum price, POST 400 with field errors, POST without price, out-of-range prices (`10000.01`, `12.345`, `1e400`), malformed JSON 400, DELETE 204, DELETE 404 payload, DELETE non-numeric id → 400, PUT → 405 and `text/plain` → 415 with the standard payload |
| `ProductServiceTest` (8) | Mockito / JUnit 5 | default paging and sort, search delegation and trimming, blank search, clamping of page/size, page capped so the offset fits in an `int`, creation (trimmed + mapped), deletion, not-found exception |
| `ProductCatalogApplicationTest` | `@SpringBootTest` | context starts, exactly 1,500 products seeded, static frontend served |

Design notes:

- The controller tests run **full stack against the seeded H2 database**, not a `@WebMvcTest` slice with a mocked
  service. Pagination and search results are only meaningful against real data. The service's logic is still
  unit-tested in isolation in `ProductServiceTest`.
- Both Spring test classes use the same configuration (`@SpringBootTest @AutoConfigureMockMvc`), so they share one
  cached application context. Two different configurations would create two contexts pointing at the same
  `jdbc:h2:mem:catalog` database, and the second context's `create-drop` would wipe the first one's data.
- Mockito is loaded as a `-javaagent` through Surefire's `argLine` (with `maven-dependency-plugin:properties` to
  resolve the jar path). Without this, JDK 21 printed a "dynamically loaded agent" warning, and future JDKs will
  refuse to self-attach.

## Verification performed

- Initial implementation: `mvn clean test` (20/20), and `mvn spring-boot:run` with manual `curl` checks: default page
  (20/1500/75), `size=1000` → 100, `search=head` → 120, POST valid → 201, POST invalid → 400 with field errors,
  DELETE → 204, repeated DELETE → 404, `/` serves the UI.
- After the review fixes: `mvn clean test` (31/31, no Mockito agent warnings), and `mvn spring-boot:run` with `curl`:
  - `price` `1e400` / `12.345` → 400, `10000.00` → 201;
  - PUT → 405 with `Allow` header and `ApiError`; `text/plain` → 415 `ApiError`; unknown file → 404 `ApiError`;
  - `page=21474837&size=100` → 200 with an empty page;
  - 20 ids × 2 concurrent DELETEs → 20×204 and 20×404 (before the fix: 12 of the 40 requests returned 500);
  - `/actuator/health` → UP, and `/` serves the UI.
- The frontend changes were checked with `node --check` (syntax only). They were **not** tested in a browser.

## What the developer should review or change

- **Architecture doc inconsistencies** (`catalog` vs `category`, "search case sensitive", 1,000 products, no `errors`
  in the validation example) were corrected in architecture.md after the review, so it now matches the code.
- `price` is a `double` in the entity, as the architecture specifies. For real money, prefer `BigDecimal` /
  `NUMERIC(…, 2)` end to end to avoid floating-point rounding.
- `ApiError.java` is one file beyond the target structure. It holds the shared error payload.
- The H2 console is enabled for local development. Disable it (`spring.h2.console.enabled: false`) in any shared
  environment.
- Test the frontend flows (search, paging, add, delete, error messages) manually in a browser before submitting.
