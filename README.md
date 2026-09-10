# Aprovexa

Aprovexa is an internal enterprise platform for managing employee requests and approval workflows in a traceable, auditable way.

The project is developed incrementally. Each version introduces one controlled capability, is validated locally and in CI, and is merged only after its acceptance criteria are satisfied.

## Version history

| Version | Scope | Status |
| --- | --- | --- |
| V0 — Foundation | Java 21, Spring Boot, Maven Wrapper, PostgreSQL container, repository baseline and CI | ✅ Closed (`v0.1.0`) |
| V1 — Request Management REST API | First request-domain vertical slice | ✅ Closed (`v1.0.0`) |
| V2 — Persistence and Audit | Flyway, database constraints, immutable history, comments and Testcontainers | ✅ Local validation complete; PR/CI pending |

## Current version — V2 Persistence and Audit

V2 makes PostgreSQL an explicit part of the application design. Hibernate no longer creates or updates the schema. Database evolution is owned by Flyway migrations, state transitions are recorded in an immutable audit history, comments are persisted, and repository/integration behaviour is tested against a real PostgreSQL container.

### Technology added in V2

- Flyway migrations
- PostgreSQL constraints and indexes
- Immutable `request_history`
- Persistent `request_comments`
- Explicit JPQL repository queries
- Testcontainers 2.x with PostgreSQL 17
- Spring Boot Testcontainers service connections

Security, JWT, RBAC and authenticated identities are deliberately not introduced until V3.

## Repository structure

```text
aprovexa/
├── backend/
│   ├── src/main/java/com/aprovexa/
│   │   ├── common/error/
│   │   ├── config/
│   │   └── request/
│   │       ├── comment/          Comment entity and repository
│   │       ├── controller/       REST API
│   │       ├── dto/              Request, audit and comment contracts
│   │       ├── history/          Immutable transition history
│   │       ├── model/            Request aggregate and lifecycle
│   │       ├── repository/       Request persistence queries
│   │       └── service/          Transactional use cases
│   ├── src/main/resources/
│   │   └── db/migration/         Versioned Flyway SQL
│   └── src/test/java/com/aprovexa/
│       ├── request/
│       │   ├── controller/
│       │   ├── model/
│       │   ├── persistence/      PostgreSQL/Testcontainers tests
│       │   └── service/
│       └── support/              Testcontainers configuration
├── frontend/                     Reserved for V11
├── infrastructure/
├── scripts/
├── .github/workflows/
├── .env.example
├── docker-compose.yml
└── README.md
```

Internal working documentation is intentionally excluded from Git.

## Database ownership — Flyway

V2 disables Hibernate schema generation:

```properties
spring.jpa.hibernate.ddl-auto=none
```

Schema evolution lives in:

```text
backend/src/main/resources/db/migration/
├── V1__create_requests.sql
└── V2__persistence_audit.sql
```

`V1__create_requests.sql` represents the request table baseline. `V2__persistence_audit.sql` adds explicit constraints and indexes plus the audit and comment tables.

### Migration bridge from V1

Local V1 databases may already contain the `requests` table because V1 temporarily used Hibernate `ddl-auto=update`. The local profile therefore enables:

```properties
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
```

Behaviour:

- empty schema → Flyway executes V1 and V2;
- existing non-empty V1 schema without Flyway metadata → Flyway records baseline `1` and applies V2.

For the cleanest V2 validation, reset the local PostgreSQL volume and let Flyway create everything from zero.

## V2 database model

### `requests`

Contains request state and content. V2 adds database-level checks for:

- allowed request types;
- allowed lifecycle states;
- title, description and requester lengths;
- timestamp consistency;
- justification maximum length.

Indexes support the main filtered/paginated access paths.

### `request_history`

Each successful lifecycle transition stores:

```text
request_id
previous_status
new_status
changed_by
changed_at
```

History is append-only. Two layers protect it:

1. the JPA entity is marked `@Immutable` and exposes no update/delete use case;
2. PostgreSQL has a trigger that rejects `UPDATE` and `DELETE` on `request_history`.

The foreign key uses `ON DELETE RESTRICT`. A request that already has transition history cannot be removed directly from the database.

### `request_comments`

Stores comments associated with a request:

```text
request_id
author
content
created_at
```

Comments are returned chronologically and paginated with a deterministic secondary `id ASC` sort.

## Transactional audit

Lifecycle transitions and their history row are stored inside the same Spring transaction.

Conceptually:

```text
load request
    ↓
validate transition
    ↓
change request status
    ↓
insert request_history
    ↓
COMMIT both
```

If the history insert fails, the request status change is rolled back. V2 includes an integration test specifically for this behaviour.

## REST API

Base path remains:

```text
/api/v1/requests
```

### Request lifecycle

| Method | Endpoint | Behaviour |
| --- | --- | --- |
| `POST` | `/api/v1/requests` | Create request |
| `GET` | `/api/v1/requests/{id}` | Get request |
| `GET` | `/api/v1/requests` | Filtered/paginated list |
| `PUT` | `/api/v1/requests/{id}` | Edit a `CREATED` request |
| `DELETE` | `/api/v1/requests/{id}` | Delete a `CREATED` request |
| `POST` | `/api/v1/requests/{id}/submit` | Submit and write history |
| `POST` | `/api/v1/requests/{id}/approve` | Approve and write history |
| `POST` | `/api/v1/requests/{id}/reject` | Reject and write history |
| `POST` | `/api/v1/requests/{id}/cancel` | Cancel and write history |

V2 requires an explicit actor for every state transition because authentication does not exist until V3:

```json
{
  "actor": "Laura"
}
```

This temporary API contract is replaced by the authenticated user identity when security is introduced.

### History

```text
GET /api/v1/requests/{id}/history
```

Returns transitions in chronological order.

### Comments

Create:

```text
POST /api/v1/requests/{id}/comments
```

```json
{
  "author": "Manager",
  "content": "Please confirm the expected delivery date."
}
```

List:

```text
GET /api/v1/requests/{id}/comments?page=0&size=20
```

## Custom request search

`RequestRepository` now uses one explicit JPQL query with optional `type` and `status` filters. Pagination and sorting remain controlled by `Pageable`.

Supported sort fields remain:

```text
id
createdAt
updatedAt
title
status
type
```

A secondary `id ASC` order is added whenever `id` is not the primary sort field.

## Run V2 locally

### Recommended clean migration validation

From the repository root:

```powershell
docker compose down -v
docker compose up -d postgres
docker compose ps
```

`down -v` is intentional for this one validation: it removes the V1 development volume so Flyway can prove that it can build the complete schema from an empty database.

### Build and run all tests

From `backend/`:

```powershell
.\mvnw.cmd clean verify
```

V2 integration tests require Docker because Testcontainers launches a real PostgreSQL 17 container.

Expected final result:

```text
BUILD SUCCESS
```

### Start the backend

```powershell
.\mvnw.cmd spring-boot:run
```

or run `BackendApplication` from IntelliJ IDEA using Java 21.

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

## Inspect Flyway and audit data

List Flyway migrations:

```powershell
docker compose exec postgres psql -U aprovexa -d aprovexa -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Inspect tables:

```powershell
docker compose exec postgres psql -U aprovexa -d aprovexa -c "\dt"
```

Inspect request history:

```powershell
docker compose exec postgres psql -U aprovexa -d aprovexa -c "SELECT id, request_id, previous_status, new_status, changed_by, changed_at FROM request_history ORDER BY id;"
```

Inspect comments:

```powershell
docker compose exec postgres psql -U aprovexa -d aprovexa -c "SELECT id, request_id, author, content, created_at FROM request_comments ORDER BY id;"
```

## Automated tests in V2

V2 keeps the V1 unit/MVC suite and adds real PostgreSQL integration coverage for:

- Flyway creation of the complete schema;
- Flyway migration history;
- database constraints;
- filtered request queries and stable pagination;
- transition history persistence;
- database-level history immutability;
- comment persistence and pagination;
- rollback of a request status change when the audit insert fails.

Testcontainers uses the same PostgreSQL major version as the local Compose environment.

## V2 validation status

| Check | Status |
| --- | --- |
| Full Maven test suite | ✅ 25 tests, 0 failures, 0 errors, 0 skipped |
| Unit and MVC regression suite | ✅ Passed |
| Flyway migration on empty PostgreSQL | ✅ V1 and V2 applied successfully |
| Second startup with no schema changes | ✅ Schema history unchanged |
| Database constraints | ✅ Covered by PostgreSQL integration tests |
| Immutable history | ✅ Integration test and direct PostgreSQL `UPDATE` rejection |
| Transaction rollback | ✅ Covered by integration test |
| Repository tests with Testcontainers | ✅ PostgreSQL 17 container passed |
| Comments and history through Swagger | ✅ Manually validated |
| Direct `psql` inspection | ✅ Tables, migrations, history and comments verified |
| pgAdmin inspection | ✅ Schema and persisted data visually verified |
| `mvnw clean verify` | ✅ `BUILD SUCCESS` |
| GitHub Actions | ✅ Pending pull request |

## Git workflow for V2

Development branch:

```text
feature/v2-persistence-and-audit
```

Planned implementation commit:

```text
feat(v2): add migrations and request audit
```

Target tag after all gates succeed:

```text
v2.0.0
```

All local and manual V2 gates are validated. V2 remains open only until the pull request passes GitHub Actions, the README is marked closed, the branch is merged to `main`, and tag `v2.0.0` is created.
