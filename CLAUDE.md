# PromptVault — Project Guide for Claude Code

PromptVault is a reactive web application that lets developers organize, version, and evaluate the prompts they use with AI coding assistants. Each user has a private workspace containing collections of prompts, every prompt can be edited and snapshotted as immutable versions, and a dashboard shows aggregate statistics.

The project has six modules — five backend modules (each planned and implemented in its own session) plus a frontend module built afterwards as a separate phase; this guide keeps conventions consistent across those sessions.

## Tech stack (fixed — do not substitute)

- **Language / runtime:** Java 21
- **Backend:** Spring Boot (latest stable) with Spring WebFlux — fully reactive, non-blocking
- **Security:** Spring Security (WebFlux Security) with stateless JWT authentication
- **Persistence:** Spring Data R2DBC over embedded H2, using the `io.r2dbc:r2dbc-h2` driver
- **Frontend:** React with Tailwind CSS
- **Build:** Maven (use the Maven wrapper `./mvnw` when present, otherwise `mvn`)

## Non-negotiable rules

These apply to every module. Treat a violation as a bug.

- **Never introduce a blocking call into a reactive pipeline.** No JPA/Hibernate, no blocking JDBC, no `.block()` in production code. Persistence is R2DBC only.
- **Controllers return `Mono<T>` or `Flux<T>`.** Services and repositories are reactive end to end.
- **Every data operation is owner-scoped.** Reads, updates, and deletes must be filtered by the authenticated owner taken from the security context. A user must never be able to access another user's data. This is the single most important correctness rule in the app.
- **Security is stateless.** No server-side sessions. The JWT carries identity; protected endpoints require a valid `Authorization: Bearer <token>`.
- **Passwords are stored only as BCrypt hashes.** Never persist or log a plaintext password.
- **Secrets come from configuration**, never hard-coded (JWT signing secret in particular).

## Modules

Implement in this order; each builds on the previous. The dashboard is last because it reads counts from all other tables.

1. **Authentication** — register, login, logout, JWT issuance and validation. Endpoints: `POST /auth/register`, `POST /auth/login`, `GET /auth/me`. Built first because every other module depends on an authenticated owner.
2. **Collections** — named categories owned by a user; each prompt belongs to exactly one collection. Endpoints: `GET /collections`, `POST /collections`, `DELETE /collections/{id}`. Decide and document the delete semantics for prompts inside a removed collection (cascade vs. block).
3. **Prompt Management (main feature)** — CRUD over prompts. `PATCH` updates only the fields present in the request. Endpoints: `GET /prompts`, `POST /prompts`, `PATCH /prompts/{id}`, `DELETE /prompts/{id}`.
4. **Version History** — a `PromptVersion` is an immutable snapshot (promptId, content, versionNumber, createdAt). Save assigns the next sequential version number; restore copies an old snapshot's content back onto the live prompt. Endpoints: `POST /prompts/{id}/versions`, `GET /prompts/{id}/versions`. Be careful: the "read max version then increment" flow is a known race-condition risk — express it as a clean reactive pipeline and flag the concurrency caveat.
5. **Dashboard** — a single reactive endpoint, `GET /dashboard`, returning total collections, total prompts, total versions, and the latest prompt for the current user. Combine the independent count queries concurrently with `Mono.zip`. **Do NOT implement SSE or any real-time streaming** — plain aggregation only.

Module 6 is the **Frontend** — see the dedicated section below. It is built as a separate phase after all five backend modules are complete.

## Frontend

The frontend is built **after** the backend is complete — all five backend modules ship and are tested first, then the React + Tailwind frontend is built as a separate phase against the finished API.

**Pages and routing:**
- `/` — redirects to /dashboard when a valid token is present, otherwise to /login. 
- `/login` — Login (and register) page. Public.
- `/dashboard` — Dashboard with the four aggregate metric cards. This is the post-login home screen: a successful login redirects here.
- `/collections` — Collections list, shown as a sidebar, with a "New Collection" action.
- `/prompts/{id}` — Prompt Editor (title + content), including the version-history panel and restore action.

There is no separate public landing page; the Dashboard serves as the application's home screen for an authenticated user, and the sidebar (collections) is reachable from every protected page.
All routes except `/login` are protected: if there is no valid token in state, redirect to `/login`.

**JWT handling (client side):**

- The token is held **in memory only** (React state / context) — it is intentionally **not** persisted to localStorage or cookies.
- Attach it as `Authorization: Bearer <token>` on every API request.
- On a `401` response, clear the token from state and redirect to `/login`.
- **Known trade-off:** because the token lives only in memory, a full page refresh or a new tab logs the user out and returns them to `/login`. This is acceptable for the exam build. Practical note for the demo: do not refresh the page mid-session while capturing screenshots, or you will be logged out.

## Testing strategy

Every module ships with tests. Two tools:

- **`WebTestClient`** for endpoint/integration tests (status codes, request/response bodies, auth flows).
- **`StepVerifier`** (from `reactor-test`) for unit-testing reactive streams at the service layer.

Required coverage per module includes the happy path, the unauthorized/forbidden path, and at least one ownership-isolation test proving user A cannot reach user B's data.

## Workflow conventions

- Build order is backend-first: implement and test all five backend modules (one module per session), then build the React + Tailwind frontend as a separate phase.
- Keep each backend module a single, complete, working unit before moving to the next session.
- Match the patterns established in earlier modules (repository → service → reactive controller) rather than inventing new ones.
- Build and run tests with Maven: `./mvnw clean verify` (or `mvn clean verify`) before considering a module done; run the app with `./mvnw spring-boot:run`.
- At the end of each session, capture the real prompts used and the testing notes for the exam document while they are fresh — do not reconstruct them later from memory.

## Out of scope (intentionally excluded)

Tags, search, favorites, experiment logs, export, notifications, real-time updates, and any AI integration. The project is deliberately small and complete; do not add these unless explicitly asked.
