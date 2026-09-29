# AI Usage — Frontend Prototype

- Purpose: Document how AI was used to create a simple frontend prototype for the Product Catalog exercise.

- AI agent used: Claude (assisted to generate frontend prototype files).

- Prompt used: The user asked for a single-page frontend prototype (HTML/CSS/JS) that demonstrates search, pagination, add and delete product flows. The frontend should mock the following endpoints: `GET /api/products`, `POST /api/products`, `DELETE /api/products/{id}` and be implemented with vanilla JS, CSS, and HTML. The user requested approx. 20 mock products and a docs file explaining the AI usage.

- Summary of response: The AI generated a small frontend project in `frontend/` with `index.html`, `styles.css`, and `app.js`. It implemented a local mock API using `localStorage` and provided search, pagination, add, and delete capabilities. A documentation file was created at `docs/ai/frontend-prototype.md` describing the interaction.

- What was generated:
  - frontend/index.html — main single-page UI
  - frontend/styles.css — responsive, simple styling
  - frontend/app.js — application logic and mocked API
  - docs/ai/frontend-prototype.md — this AI usage documentation

- Important implementation decisions:
  - The mock backend is implemented in-browser using `localStorage` under key `__mock_products_v1` so data persists across refreshes.
  - Pagination defaults to `size=5` to demonstrate multiple pages with ~20 items; the mock API supports `page` and `size` parameters and filtering by `search`.
  - The code is structured so `mockGetProducts`, `mockAddProduct`, and `mockDeleteProduct` mirror the real API shape and return Promises, making replacement with `fetch()` straightforward.
  - Basic validation is performed on the add form (required fields). The mock `POST` rejects if validation fails.
  - The UI shows simple loading and error states via a `status` element.

- What should be reviewed or changed by the developer:
  - Replace mock functions in `app.js` with real `fetch()` calls to the backend endpoints when the Spring Boot API is available.
  - Adjust `size` default and maximum limits to match backend constraints.
  - Improve accessibility attributes (aria-*), labeling, and keyboard focus management as needed.
  - Add more robust error handling for network and server errors.

- Notes on provenance: The AI generated the project files programmatically. The developer should review and adapt naming conventions or integrate the files into the larger project structure.

- Update: the prototype has since been moved to `src/main/resources/static/`, and the mocks were replaced with real `fetch()` calls to the Spring Boot API. See [implementation.md](implementation.md#frontend-integration).
