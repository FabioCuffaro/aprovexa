# Aprovexa

Aprovexa is an internal enterprise platform for managing employee requests and approval workflows in a traceable, auditable way.

The project is developed incrementally. Each version introduces one controlled capability, is validated locally and in CI, and is merged only after its acceptance criteria are satisfied.

## Version history

| Version | Scope | Status |
| --- | --- | --- |
| V0 — Foundation | Java 21, Spring Boot, Maven Wrapper, PostgreSQL container, repository baseline and CI | ✅ Closed (`v0.1.0`) |
| V1 — Request Management REST API | First request-domain vertical slice | 🚧 In validation |

## Current version — V1 Request Management REST API

V1 introduces the first real business behaviour of Aprovexa in a single Spring Boot backend. The objective is to stabilize the request lifecycle before adding audit maturity, security, Kafka or the Angular client.

### Technology used in V1

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Jakarta Bean Validation
- Spring Data JPA
- PostgreSQL 17
- springdoc OpenAPI / Swagger UI
- JUnit 5, Mockito and AssertJ
- GitHub Actions

No Spring Security, Kafka, Flyway, microservices or Angular code is introduced in this version.

## Repository structure

```text
aprovexa/
├── backend/
│   ├── src/main/java/com/aprovexa/
│   │   ├── common/error/        Shared API error handling
│   │   ├── config/              OpenAPI configuration
│   │   └── request/             V1 request feature
│   │       ├── controller/      REST endpoints
│   │       ├── dto/             API input/output contracts
│   │       ├── model/           JPA entity, type and lifecycle state
│   │       ├── repository/      Spring Data persistence
│   │       └── service/         Application operations
│   └── src/test/java/com/aprovexa/
├── frontend/                    Reserved for V11
├── infrastructure/
├── scripts/
├── .github/workflows/
├── .env.example
├── docker-compose.yml
└── README.md
```

Internal working documentation is intentionally excluded from Git.

## Request model

A request starts in `CREATED` and supports the following types:

- `VACATION`
- `PURCHASE`
- `ACCESS`
- `INCIDENT`
- `OTHER`

Lifecycle:

```text
CREATED --submit--> IN_REVIEW --approve--> APPROVED
   |                    |
   |                    +--reject-----> REJECTED
   |
   +--cancel--------------------------> CANCELLED

IN_REVIEW --cancel--------------------> CANCELLED
```

### V1 business rules

- New requests always start in `CREATED`.
- Only `CREATED` requests can be edited or deleted.
- `submit` only accepts `CREATED` requests.
- `approve` and `reject` only accept `IN_REVIEW` requests.
- `cancel` accepts unresolved requests (`CREATED` or `IN_REVIEW`).
- An operation that is valid conceptually but incompatible with the current state returns HTTP `409 Conflict`.
- Request entities are never returned directly by the REST API; DTOs define the public contract.

## REST API

Base path:

```text
/api/v1/requests
```

| Method | Endpoint | Behaviour |
| --- | --- | --- |
| `POST` | `/api/v1/requests` | Create a request (`201`) |
| `GET` | `/api/v1/requests/{id}` | Retrieve one request (`200` / `404`) |
| `GET` | `/api/v1/requests` | Paginated list with `type` and `status` filters |
| `PUT` | `/api/v1/requests/{id}` | Edit a `CREATED` request |
| `DELETE` | `/api/v1/requests/{id}` | Delete a `CREATED` request (`204`) |
| `POST` | `/api/v1/requests/{id}/submit` | `CREATED → IN_REVIEW` |
| `POST` | `/api/v1/requests/{id}/approve` | `IN_REVIEW → APPROVED` |
| `POST` | `/api/v1/requests/{id}/reject` | `IN_REVIEW → REJECTED` |
| `POST` | `/api/v1/requests/{id}/cancel` | unresolved → `CANCELLED` |

List query parameters:

```text
type      optional RequestType
status    optional RequestStatus
page      default 0
size      default 20, maximum 100
sortBy    default createdAt
direction default DESC
```

A secondary `id ASC` sort is added when needed so pagination remains deterministic for rows with the same primary sort value.

## HTTP behaviour

V1 normalizes the main response codes:

- `200 OK` — successful reads, updates and transitions
- `201 Created` — request creation
- `204 No Content` — deletion
- `400 Bad Request` — validation, malformed JSON, invalid enum/query values
- `404 Not Found` — unknown request id
- `409 Conflict` — invalid lifecycle transition or operation for the current state

Validation errors use a structured payload containing a stable error code and field-level details.

## PostgreSQL persistence

Start PostgreSQL from the repository root:

```powershell
docker compose up -d postgres
docker compose ps
```

The local Spring profile connects to:

```text
jdbc:postgresql://localhost:5432/aprovexa
```

V1 deliberately uses:

```properties
spring.jpa.hibernate.ddl-auto=update
```

This is **temporary for V1**. V2 replaces automatic schema management with explicit, versioned Flyway migrations and PostgreSQL integration tests.

## Run V1 locally

### 1. Start PostgreSQL

From the repository root:

```powershell
docker compose up -d postgres
docker compose exec postgres pg_isready -U aprovexa -d aprovexa
```

Expected readiness message contains:

```text
accepting connections
```

### 2. Build and test

From `backend/`:

```powershell
.\mvnw.cmd clean verify
```

Expected:

```text
BUILD SUCCESS
```

### 3. Start Spring Boot

From IntelliJ IDEA, run `BackendApplication`, or from `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

The application runs on:

```text
http://localhost:8080
```

### 4. Open Swagger UI

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

## Example — purchase request

Create:

```json
{
  "type": "PURCHASE",
  "title": "Development laptop",
  "description": "Laptop required for backend development work",
  "justification": "Current equipment is insufficient",
  "requester": "Laura"
}
```

Expected initial status:

```text
CREATED
```

Then execute:

```text
POST /api/v1/requests/{id}/submit
POST /api/v1/requests/{id}/approve
```

Expected lifecycle:

```text
CREATED → IN_REVIEW → APPROVED
```

Calling `approve` directly from `CREATED` must return `409 Conflict`.

## Automated tests included in V1

- Request lifecycle unit tests.
- Invalid transition tests.
- Service tests with mocked persistence.
- MVC controller tests for `201`, `400` and `404` behaviour.

The database itself is still validated manually against PostgreSQL in V1. Repository integration tests with real PostgreSQL/Testcontainers are introduced in V2 as planned.

## V1 validation status

| Check | Status |
| --- | --- |
| Request lifecycle tests | ✅ Passed locally via `mvnw clean verify` |
| Controller tests | ✅ Passed locally via `mvnw clean verify` |
| `mvnw clean verify` | ✅ `BUILD SUCCESS` |
| PostgreSQL startup | ✅ PostgreSQL healthy and JPA/Hikari connection verified |
| Java 21 runtime | ✅ IntelliJ runtime corrected and verified on Java 21.0.12 |
| Swagger UI / OpenAPI | ✅ Swagger UI loaded and request endpoints discovered by springdoc |
| CRUD through Swagger | ⏳ Pending manual validation |
| Valid transitions | ✅ `CREATED → IN_REVIEW → APPROVED` validated in Swagger |
| Invalid transition → `409` | ✅ Re-approving an `APPROVED` request correctly returned `409 Conflict` |
| Pagination and filters | ⏳ Pending manual validation |
| GitHub Actions | ⏳ Pending pull request |

## Git workflow for V1

Development branch:

```text
feature/v1-request-management-rest-api
```

Planned main implementation commit:

```text
feat(v1): implement request management API
```

Target tag after all validation and merge steps succeed:

```text
v1.0.0
```

V1 is not considered closed until local tests, manual API checks, CI and the final README state are all validated.
