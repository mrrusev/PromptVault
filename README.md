# PromptVault

A reactive web application for organizing, versioning, and evaluating the prompts you use
with AI coding assistants. Each user has a private workspace of **collections**; every
**prompt** belongs to a collection and can be edited and snapshotted as immutable
**versions**; a **dashboard** shows aggregate statistics for the workspace.

Built as a SoftUni *AI-Assisted Development* project — the backend ships as five modules,
followed by a React frontend, all developed with Claude Code (see
[AI-assisted development](#ai-assisted-development) below).

## Tech stack

| Layer        | Technology |
|--------------|------------|
| Language     | Java 21 |
| Backend      | Spring Boot 3.5, Spring WebFlux (fully reactive, non-blocking) |
| Security     | Spring Security (WebFlux) with stateless JWT |
| Persistence  | Spring Data R2DBC over embedded H2 (`io.r2dbc:r2dbc-h2`) |
| Frontend     | React 19 + React Router 7 + Tailwind CSS 4, built with Vite + TypeScript |
| Build        | Maven (`./mvnw`) for the backend, npm for the frontend |
| Tests        | WebTestClient + StepVerifier (backend); Vitest + React Testing Library + MSW (frontend) |

## Architecture & conventions

These rules are enforced across every module:

- **Reactive end to end** — controllers return `Mono<T>`/`Flux<T>`; no blocking calls, no
  JPA/Hibernate, no `.block()` in production code. Persistence is R2DBC only.
- **Owner-scoped data access** — every read, update, and delete is filtered by the
  authenticated owner from the security context. A user can never reach another user's data.
- **Stateless security** — no server sessions; a JWT bearer token carries identity. Passwords
  are stored only as BCrypt hashes; the signing secret comes from configuration, never code.
- **In-memory token on the client** — the JWT lives in React state only (not localStorage), so
  a full page refresh logs the user out. This is an intentional trade-off for the exam build.

## Modules

| # | Module | Highlights |
|---|--------|-----------|
| 1 | **Authentication** | `POST /auth/register`, `POST /auth/login`, `GET /auth/me`; JWT issuance/validation |
| 2 | **Collections** | `GET/POST /collections`, `DELETE /collections/{id}`; named, owner-scoped categories |
| 3 | **Prompt management** | `GET/POST /prompts`, `PATCH /prompts/{id}` (partial), `DELETE /prompts/{id}` |
| 4 | **Version history** | `POST /prompts/{id}/versions` (snapshot), `GET /prompts/{id}/versions`, `POST /prompts/{id}/versions/{n}/restore`; sequential version numbers guarded by a `UNIQUE(prompt_id, version_number)` constraint + reactive retry |
| 5 | **Dashboard** | `GET /dashboard` combines independent counts with `Mono.zip` (no SSE / streaming) |
| 6 | **Frontend** | Login/Register, Dashboard, Collections (sidebar + prompt list), Prompt Editor with version-history panel and restore |

### Frontend pages & routing

- `/` — redirects to `/dashboard` when a token is present, otherwise `/login`.
- `/login` — Login / Register (public).
- `/dashboard` — four aggregate metric cards + latest prompt (post-login home).
- `/collections` — collections sidebar; the selected collection lists its prompts and offers
  a **New prompt** action that creates a prompt and opens it in the editor.
- `/prompts/{id}` — Prompt Editor: edit title/content, **Save**, **Save version**, **Restore**
  a snapshot, **Delete**. All routes except `/login` are protected; a `401` clears the token
  and redirects to `/login`.

## Running locally

**Backend** (serves on `http://localhost:8080`):

```bash
./mvnw spring-boot:run          # run
./mvnw clean verify             # build + full test suite
```

**Frontend** (Vite dev server, proxies `/api` → `http://localhost:8080`):

```bash
cd frontend
npm install
npm run dev                     # dev server (http://localhost:5173)
npm run verify                  # typecheck + lint + test + production build
```

Start the backend first, then the frontend. To try it: register → create a collection →
**New prompt** → write content → **Save** → **Save version** → **Restore**. (Don't refresh
mid-session: the token is in memory only.)

## AI-assisted development

This project was built with **Claude Code**. The tooling that shaped the workflow lives under
`.claude/` and is committed with the repo.

### Skills

| Skill | Source | Role in this project |
|-------|--------|----------------------|
| [`vercel-react-best-practices`](.claude/skills/vercel-react-best-practices) | `vercel-labs/agent-skills` (pinned in `skills-lock.json`) | React/Tailwind performance and effect-dependency guidance consulted whenever building or refactoring frontend components (Login, Dashboard, Collections, Prompt Editor). |
| [`delegate-implementation`](.claude/skills/delegate-implementation) | local | Breaks feature work into focused sub-problems delegated to specialist subagents instead of editing everything inline. |
| `run` | built-in | Launches the real backend + frontend and drives the UI in a browser to verify a change actually works (not just unit tests). |

Specialist **subagents** carry the actual implementation and exploration: `backend-expert`
and `frontend-expert` (defined in `.claude/agents/`) for the two stacks, plus the built-in
`Explore` and `Plan` agents for codebase research and design.

### The session-usage hook

`.claude/settings.json` defines a hook that records which skills and subagents each turn
actually used. It has four stages:

1. **`UserPromptSubmit`** — writes the first ~150 characters of the prompt to a per-session
   temp file (`/tmp/cc-prompt-<session>.txt`).
2. **`PostToolUse` (matcher `Skill`)** — appends `SKILL <name>` to a per-session usage file
   each time a skill is invoked.
3. **`PostToolUse` (matcher `Agent`)** — appends `AGENT <subagent_type>` each time a subagent
   is launched.
4. **`Stop`** — aggregates the prompt + skill/agent counts into
   [`.claude/session-usage.log`](.claude/session-usage.log) (one line per turn) and surfaces a
   short "turn summary" back in the session.

The result is an auditable record of *how* the app was built — which prompt led to which
skills and agents — which is the point of the AI-Assisted Development exercise.

## Out of scope

Tags, search, favorites, experiment logs, export, notifications, real-time updates, and any AI
integration are intentionally excluded to keep the project small and complete.
