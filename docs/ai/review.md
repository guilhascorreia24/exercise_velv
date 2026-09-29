# AI Usage: Final Technical Review

## Summary

- **Purpose:** Final technical review of the Product Catalog before submission. It checks the implementation against
  [architecture.md](architecture.md), [frontend-prototype.md](frontend-prototype.md),
  [implementation.md](implementation.md) and the original exercise requirements.
- **AI agent used:** Claude (Claude Code, Opus 5.5), acting as a senior Java / Spring Boot reviewer.
- **Date:** 2026-09-29
- **Reviewed revision:** commit `ff8631a` ("Implement Product Catalog Backend with REST API and Frontend Integration").
  It includes the `spring-boot-starter-actuator` dependency that the developer added after the AI implementation.
- **Code changes made during the review:** none. Only this document was created.
- **Follow-up:** the developer then asked Claude to apply the suggested corrections. What was done and how it was
  verified is recorded in [Decisions on suggestions](#decisions-on-suggestions). The findings and checklist below
  describe the code **as reviewed**, before those fixes.

## Prompt used

> Before making any further code changes, perform a final technical review of the current project. Do NOT modify
> the code yet. Review the implementation against architecture.md, frontend-prototype.md, implementation.md and the
> original Product Catalog exercise requirements. Focus on: (1) Architecture: Controller → Service → Repository,
> separation of responsibilities, DTO usage; (2) Backend: REST API design, validation, pagination, search, delete
> behaviour, error handling, H2 configuration, 1,500 seeded products; (3) Frontend: API integration, search,
> pagination, add, delete, loading/error states; (4) Tests: are they sufficient, what is untested; (5) Code quality:
> unnecessary complexity, potential bugs, inconsistencies with architecture.md, anything that could be questioned in
> the technical interview; (6) a requirements checklist (Requirement / PASS-PARTIAL-MISSING / Evidence / Action);
> (7) the 10 most important technical decisions to explain in the interview. Also create docs/ai/review.md recording
> that Claude performed the review, the prompt, main findings, suggested changes and which suggestions were
> accepted/rejected and why. Do not invent changes or claim that something was tested if it was not actually verified.

## How the review was verified

| What | How | Result |
|------|-----|--------|
| Source review | Read every file under `src/`, plus `pom.xml`, `application.yml` and all docs | Findings below |
| Test suite | `mvn clean test` on the reviewed revision | 20/20 passing (11 controller, 7 service, 2 application) |
| Runtime behaviour | `mvn spring-boot:run` on port 18080, probed with `curl` | Results quoted in the findings |
| Startup ordering | Application log | `Seeded 1500 products` is logged before `Tomcat started` |
| Frontend | **Code reading only.** No browser (manual or automated) was used in this review | Frontend findings are from code inspection, not observed behaviour |

> Process note: the first `curl` run accidentally hit an instance the developer already had running on port 8080
> (the VS Code launch configuration). It created and deleted some in-memory products, and the cleanup step stopped
> that instance. All results in this document come from a second, isolated run on port 18080.

## Main findings

Severity: **High** = incorrect behaviour a reviewer can easily trigger. **Medium** = inconsistency or robustness
gap. **Low** = edge case or polish.

### Architecture: sound

- Controller → Service → Repository is appropriate for the scope, and the responsibilities match architecture.md.
  The controller only binds, validates (`@Valid`) and sets status codes. The service owns paging rules, trimming,
  transactions and the not-found rule. The repository is a single derived query.
- DTOs are records, and the entity is never serialised. The service returns DTOs rather than entities. That is
  acceptable here, but it couples the service to the API contract, and an interviewer may ask about it.
- No unnecessary layers (mappers, interfaces-per-service, etc.).

### Findings

| ID | Severity | Area | Finding | Evidence |
|----|----------|------|---------|----------|
| F1 | High | Validation | `price` has no upper bound and no scale limit. `1e400` is accepted and stored as `Infinity`, and the API then returns `"price":"Infinity"`, a **string** where the contract promises a number. `12.3456` and `0.001` are also accepted. | Verified: `POST {"price":1e400}` → `201 {"price":"Infinity"}`; `POST {"price":12.3456}` → `201`. `CreateProductRequest.java:17-19` only has `@NotNull @Positive`. |
| F2 | Medium | Error handling | The "standardised JSON payload" only covers the handled cases. 405, 415 and unexpected 500s return Spring's default body (`error`, `path`, a different timestamp format, no `message`). | Verified: `PUT /api/products` → 405 `{"timestamp":…,"status":405,"error":"Method Not Allowed","path":…}`; `POST` with `text/plain` → 415, same shape. |
| F3 | Medium | Delete | Two concurrent DELETEs of the same id: the loser gets **500** instead of 404, from `ObjectOptimisticLockingFailureException` (`findById` then `delete` is not atomic). | Verified: 20 ids × 2 parallel requests → 20× `204`, 8× `404`, 12× `500`; the exception appears in the log. |
| F4 | Medium | Build / docs | `spring-boot-starter-actuator` was added after the implementation. It is not in architecture.md, implementation.md or the README, and nothing uses it. It exposes `/actuator` and `/actuator/health`. | Verified: `GET /actuator/health` → `{"status":"UP"}`. `pom.xml:26-29`. |
| F5 | Medium | Docs | architecture.md still contradicts the code in places: the POST example uses `catalog` (the code uses `category`); the Repository section says "search case sensitive"; the error examples have no `errors` field; it says "1000 products". implementation.md documents the resolutions, but the architecture doc itself was not updated. | `architecture.md` lines ~200, ~251-277, ~300-314. |
| F6 | Low | Pagination | A very large `page` (offset > `Integer.MAX_VALUE`) returns **500**. | Verified: `?page=21474837&size=100` → 500, `InvalidDataAccessApiUsageException: Page offset exceeds Integer.MAX_VALUE`. |
| F7 | Low | Pagination | `size=0` (or negative) is clamped to **1**, not to the default 20. It is defensible but surprising. | Verified: `?page=-5&size=0` → `"page":0,"size":1`. |
| F8 | Low | Validation | `@Size(max=255)` is checked **before** the service trims, so `" " + 255 chars` is rejected even though the stored value would fit. | Verified: 256-char padded name → 400. Negligible in practice. |
| F9 | Low | Frontend | In `doAdd`/`doDelete`, `load()` swallows its own errors and the success flash then overwrites them. If the reload fails after a successful add or delete, the user sees "added"/"deleted" and the error disappears. | Code: `app.js` `doAdd` / `doDelete` call `await load()` then `flashStatus(...)`. Not observed in a browser. |
| F10 | Low | Frontend | `flashStatus`'s `setTimeout(hideStatus)` is never cancelled, so it can hide a later error message. Paging buttons stay enabled during a request, and responses can arrive out of order on fast clicking (no `AbortController`). | Code inspection only. |
| F11 | Low | Frontend UX | New products get the highest id and the list is sorted `id ASC`, so a new product lands on the **last page** and is not visible after adding. The flash message shows the new id. | Code + API behaviour (sorting verified). |
| F12 | Low | Frontend a11y | Inputs rely on `placeholder` and have no `<label>`. frontend-prototype.md already listed accessibility as a follow-up. | `index.html` |
| F13 | Low | Security / config | The H2 console is enabled with the `sa` user and an empty password, and no endpoint has authentication. This is fine for a local exercise; say so if asked. | `application.yml`; `GET /h2-console/` → 200. |
| F14 | Low | Tests / build | Mockito prints a "dynamically loaded agent" warning on JDK 21. | `mvn test` output. |

### Things that work as intended (verified)

- `GET /api/products`: defaults `page=0`, `size=20`, sorted `id ASC`; `size` capped at 100; pages past the end
  return an empty `items` list; non-numeric `page`/`size`/`id` → 400 with `ApiError`; `sort=` is ignored, so clients
  cannot trigger sorting on arbitrary properties.
- Search: partial and case-insensitive (`HEAD` → 120 results); `%` and `_` are matched literally, not as
  wildcards (`search=%` → 0 results).
- `POST`: missing price → `400 {"errors":{"price":"Price is required"}}`; unknown JSON fields (including `id`) are
  ignored, so a client cannot choose the id; an empty body → 400 "Malformed request body".
- `DELETE`: 204, then 404 with `ApiError` (single-request case).
- H2 at `jdbc:h2:mem:catalog`, `create-drop`, exactly 1,500 products, seeded before the HTTP port opens.

## Tests

**Enough for the exercise?** Yes. Every case listed in architecture.md and the acceptance criteria is covered, and
the tests check real behaviour rather than padding coverage.

**Important behaviour that is still untested** (the items marked "verified manually" were checked with `curl` in
this review, not by an automated test):

| Behaviour | Status |
|-----------|--------|
| POST with missing `price` → "Price is required" (the reason `price` is a boxed `Double`) | Verified manually only |
| Search combined with pagination (`search=head&page=1`) | Untested |
| DELETE with non-numeric id → 400 | Verified manually only |
| Page past the end → empty `items` | Verified manually only |
| `@Size(max=255)` → 400 | Verified manually only |
| 405 / 415 response shape | Untested, and inconsistent (F2) |
| Price upper bound / Infinity | Untested, and a bug (F1) |
| Concurrent delete | Untested, and a bug (F3) |
| Seed completes before the HTTP port opens | Log-verified only; no automated test |
| Frontend (all flows) | No automated tests; not browser-verified in this review |

**Test design points an interviewer may probe:**

- `ProductControllerTest` is a full-stack test (`@SpringBootTest` + MockMvc), not a `@WebMvcTest` slice. The name
  suggests a controller unit test.
- The tests assert concrete ids (1, 21, 2) and counts (1,500, 120). That works because each test rolls back and the
  identity column starts at 1 in the shared context, but it couples the tests to the seed data.
- `ProductCatalogApplicationTest` asserts `count == 1500`. That relies on `ProductControllerTest` rolling back its
  writes, which it does via `@Transactional`.

## Requirements checklist

| # | Requirement | Status | Evidence | Recommended action |
|---|-------------|--------|----------|--------------------|
| 1 | Compiles and runs with `mvn spring-boot:run`, Java 21 | PASS | `pom.xml` `java.version=21`; started in this review | — |
| 2 | H2 in memory `jdbc:h2:mem:catalog` | PASS | `application.yml`; startup log | — |
| 3 | `DataInitializer` loads exactly 1,500 products (name, category, price) | PASS | `DataInitializer` 20×3×25; `ProductCatalogApplicationTest`; log | — |
| 4 | GET pagination, defaults `page=0`, `size=20` | PASS | `ProductController.list`; controller test | — |
| 5 | `size <= 100`, never returns all products | PASS | `ProductService.findProducts` clamp; test `listCapsPageSizeAtOneHundred` | Optional: F6, F7 |
| 6 | Search by name, case-insensitive | PASS | `findByNameContainingIgnoreCase`; test `searchIsPartialAndCaseInsensitive` | — |
| 7 | Returns `ProductPageResponse` | PASS | `ProductPageResponse` record | — |
| 8 | POST with `@Valid` (name/category not blank, price > 0), 201 + `ProductResponse` | PASS | `CreateProductRequest`; tests for 201/400 | **F1**: add an upper bound and scale on price |
| 9 | DELETE 204; missing id → `ProductNotFoundException` → 404 | PASS | `ProductService.delete`; tests | F3: concurrent-delete 500 |
| 10 | `GlobalExceptionHandler`: 404 and validation 400 with a standard JSON payload | PASS | `GlobalExceptionHandler`, `ApiError` | F2: extend to 405/415/500 for full consistency |
| 11 | `ProductControllerTest` (paged GET, search, POST 201/400, DELETE 204/404) | PASS | 11 tests | Add the gaps listed above |
| 12 | `ProductServiceTest` (creation, removal, search, 404) | PASS | 7 tests | — |
| 13 | At least one `@SpringBootTest` that checks startup (architecture.md) | PASS | `ProductCatalogApplicationTest` | — |
| 14 | Static files in `src/main/resources/static/` use `fetch()` | PASS | `app.js` `request()`; `/` and `/app.js` served (test) | — |
| 15 | Frontend: search, pagination, add, delete, loading/error states | PASS (code review only) | `app.js` | F9–F12; do a manual browser pass before submitting |
| 16 | `pom.xml` and `application.yml` | PASS | Present | F4: document or remove Actuator |
| 17 | `docs/ai/implementation.md` and README with run commands | PASS | Both present | Mention Actuator if kept |
| 18 | `docs/ai/review.md` | PASS | This file | Fill in the decisions table below |
| 19 | Target project structure | PASS (minor deviation) | Matches, plus `exception/ApiError.java` | Be ready to justify `ApiError` |
| 20 | Consistency with architecture.md | PARTIAL | Contradictions listed in F5 | Update architecture.md |
| 21 | Sort `id ASC` (architecture.md) | PASS | `ProductService.DEFAULT_SORT` | — |
| 22 | Entity not exposed as the API contract (architecture.md) | PASS | Record DTOs | — |

## Suggested changes

Ordered by importance. Each has a small, local fix.

| # | Change | Fixes | Suggested approach | Effort |
|---|--------|-------|--------------------|--------|
| S1 | Bound and scale `price` | F1 | `@DecimalMax("1000000.00")` + `@Digits(integer = 7, fraction = 2)` on `CreateProductRequest.price`; add a controller test for `1e400` and `12.345` | Small |
| S2 | Make every error response use `ApiError` | F2 | Handle `HttpRequestMethodNotSupportedException` (405) and `HttpMediaTypeNotSupportedException` (415), and add a generic `Exception` → 500 handler that does **not** swallow `NoResourceFoundException`/`ErrorResponse` exceptions (or extend `ResponseEntityExceptionHandler`); add a 405 test | Small–medium |
| S3 | Decide on Actuator | F4 | Remove it (architecture.md says "keep it simple"), or keep it and document `/actuator/health` in README + implementation.md | Trivial |
| S4 | Align architecture.md with the code | F5 | `catalog` → `category`, remove "case sensitive", add `errors` to the 400 example, 1,000+ → 1,500 | Trivial (docs only) |
| S5 | Make delete atomic | F3 | `@Modifying @Query("delete from Product p where p.id = :id") int deleteByIdReturningCount(Long id)` and throw `ProductNotFoundException` when it returns 0; update `ProductServiceTest` | Small |
| S6 | Close test gaps | Tests | POST without price, search + page 1, DELETE `/abc` → 400 | Small |
| S7 | Guard page offset overflow | F6 | Clamp `page` so `page * size <= Integer.MAX_VALUE`, or return 400 | Trivial |
| S8 | Frontend polish | F9–F12 | Only show success when the reload succeeds; cancel the pending hide timer; disable paging buttons while loading; add `<label>`s | Small |
| S9 | Remove the Mockito agent warning | F14 | Configure `mockito-core` as a `-javaagent` in Surefire's `argLine` | Trivial |
| — | Not recommended to change | F7, F8, F11, F13 | Defensible as-is for the exercise; be ready to explain them | — |

## Decisions on suggestions

The developer accepted all suggestions (S1–S9) and asked Claude to apply them. For S3 the developer chose to keep
Actuator and document it. S1 was started by the developer (the annotations and the 10,000.00 limit) and
completed by Claude.

| Suggestion | Decision | What was done | Verification |
|------------|----------|---------------|--------------|
| S1 Bound and scale `price` | Accepted | The developer added `@DecimalMax("10000.00")` and `@Digits(integer = 5, fraction = 2)`. A new test showed that `1e400` still failed, because a `Double` becomes `Infinity` and `@Digits` then throws `HV000028` (a 500). Claude changed `CreateProductRequest.price` to `BigDecimal`; the entity stays `double`. | Parameterized test (`10000.01`, `12.345`, `1e400` → 400), `10000.00` → 201; `curl` confirmed |
| S2 Consistent `ApiError` for all errors | Accepted | `GlobalExceptionHandler` now extends `ResponseEntityExceptionHandler`. `createResponseEntity` wraps every framework error in `ApiError`, and a catch-all turns anything else into 500 "Unexpected error" (logged). | Tests for 405 (with `Allow` header) and 415; `curl`: 405, 415 and unknown file → 404, all `ApiError` |
| S3 Actuator | Accepted: kept and documented | `/actuator/health` documented in README and implementation.md. No configuration change (only `health` is exposed by default). | `curl /actuator/health` → `{"status":"UP"}` |
| S4 Align architecture.md | Accepted | The developer had already fixed `catalog` → `category`. Claude changed "1000 products" to 1,500, "case sensitive" to case-insensitive, updated the validation rules, and added `errors` plus the "every error uses this payload" note to the error section. | Docs only |
| S5 Atomic delete | Accepted | `ProductRepository.deleteProductById` (`@Modifying @Query`, returns the row count); the service throws `ProductNotFoundException` when it is 0. | Service tests updated; `curl` race: 20 ids × 2 concurrent → 20×204, 20×404, **0×500** (before: 12×500) |
| S6 Close test gaps | Accepted | Added tests: POST without price, search + page 1, DELETE `/abc` → 400, plus the S1/S2/S7 tests. | `mvn clean test`: 31/31 |
| S7 Page offset guard | Accepted | `page` is clamped to `Integer.MAX_VALUE / size`, consistent with the existing `size` clamp. | Controller test (huge page → 200, empty), service test (offset ≤ `Integer.MAX_VALUE`); `curl` confirmed |
| S8 Frontend polish | Accepted | Success only after a successful reload; requests numbered so stale responses are ignored; paging buttons disabled while loading; `page` changes only on success; pending hide timer cancelled; hidden `<label>`s; `max="10000"`/`maxlength="255"` on inputs. | `node --check` only. **Not tested in a browser.** |
| S9 Mockito agent warning | Accepted | `maven-dependency-plugin:properties` + Surefire `argLine` `-javaagent:${org.mockito:mockito-core:jar} -Xshare:off`. | `mvn clean test` output: 0 agent/CDS warnings |
| F7, F8, F11, F13 | Not changed | Kept as-is, as the review recommended (defensible for the exercise). | — |

Side effect of S2: an unknown URL opened in a browser now returns a JSON 404 instead of Spring's HTML
"Whitelabel" page.

## Interview preparation: 10 key technical decisions

1. **Layered monolith (Controller → Service → Repository).** Why this is enough here, what each layer owns, and
   why the controller has no business logic.
2. **Record DTOs, entity never exposed.** Static `from(...)` mapping instead of a mapping library. The service
   returns DTOs; be ready to discuss the alternative where the service returns entities and the controller maps.
3. **Pagination built in the service.** Plain `page`/`size` parameters instead of a `Pageable` argument; values are
   clamped rather than rejected; fixed `id ASC` sort, so clients cannot sort on arbitrary or unindexed columns;
   `Page` triggers a `COUNT` query on every request (`Slice` is the alternative when totals are not needed).
4. **Search via `findByNameContainingIgnoreCase`.** This generates `LOWER(name) LIKE %term%` with escaped
   wildcards. It cannot use a normal index, which is fine at 1,500 rows. Full-text search was explicitly out of
   scope.
5. **Validation.** Jakarta constraints on the request record. `price` is a `BigDecimal` in the request: it can be
   null, so a missing price gives "Price is required", and the exact JSON value is validated, so `1e400` does not
   become `Infinity`. Bounds are `@Positive`, `@DecimalMax("10000.00")` and `@Digits(5, 2)`. `@Size(255)` mirrors
   the column length, so an over-long name returns 400 instead of 500.
6. **`price` as `double`.** It follows architecture.md, but `BigDecimal` / `NUMERIC(p,2)` is the correct type for
   money (rounding errors, e.g. `0.1 + 0.2`). Expect this question.
7. **Error handling.** `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`. Spring decides the
   correct status for every framework exception (405, 415, 404, …); `createResponseEntity` only swaps the body for
   `ApiError {timestamp, status, message, errors?}`. The catch-all `Exception` handler is safe because the more
   specific framework handlers win, so a 404 or 405 can never become a 500.
8. **Delete semantics.** A single `DELETE … WHERE id = :id` that returns the affected row count (0 → 404). The
   alternatives: Spring Data 3's `deleteById` silently ignores a missing id, and `findById` + `delete` (the first
   version) returned 500 under concurrent deletes (F3).
9. **Seed data and H2.** Deterministic data (cartesian names, `Random(42)` prices). `SmartInitializingSingleton`
   instead of `ApplicationRunner`, because runners execute after Tomcat opens the port, which caused an empty catalog
   for about 1 s. `IDENTITY` ids disable JDBC batch inserts, so seeding runs 1,500 single INSERTs. `create-drop` +
   in-memory gives a clean database on every start. `open-in-view: false` keeps the persistence context out of
   serialisation.
10. **Test strategy.** Full-stack MockMvc tests against the real seeded H2, with `@Transactional` rollback per test,
    because paging and search are only meaningful against real data. Mockito unit tests for service rules. Both Spring
    test classes share one configuration, so there is one cached context; two contexts would share
    `jdbc:h2:mem:catalog`, and the second one's `create-drop` would wipe the first one's data.
