# Aprovexa

Aprovexa is an internal enterprise platform for managing employee requests and approval workflows in a traceable, auditable and secure way.

The project is developed incrementally. Each version introduces one controlled capability, is validated locally and in CI, and is merged only after its acceptance criteria are satisfied.

## Version history

| Version | Scope | Status |
| --- | --- | --- |
| V0 — Foundation | Java 21, Spring Boot, Maven Wrapper, PostgreSQL container, repository baseline and CI | ✅ Closed (`v0.1.0`) |
| V1 — Request Management REST API | Request-domain vertical slice and lifecycle | ✅ Closed (`v1.0.0`) |
| V2 — Persistence and Audit | Flyway, database constraints, immutable history, comments and Testcontainers | ✅ Closed (`v2.0.0`) |
| V3 — Security and RBAC | User accounts, BCrypt, JWT, roles, permissions, ownership and normalized 401/403 | ✅ Local validation complete; CI pending |

## Current version — V3 Security and RBAC

V3 replaces manually supplied identities with authenticated identities. Users register and log in with email/password, passwords are stored only as BCrypt hashes, successful login returns a signed JWT, and request operations are authorized using roles, permissions and resource ownership.

### Technology added in V3

- Spring Security
- OAuth2 Resource Server JWT support
- BCrypt password hashing
- symmetric HS256 JWT signing and validation
- stateless `SecurityFilterChain`
- roles `USER`, `MANAGER`, `ADMIN`
- explicit permission model
- request ownership checks in the application layer
- normalized JSON `401 Unauthorized` and `403 Forbidden`
- security integration tests against PostgreSQL/Testcontainers
- OpenAPI Bearer authentication support

## Repository structure

```text
aprovexa/
├── backend/
│   ├── src/main/java/com/aprovexa/
│   │   ├── auth/
│   │   │   ├── controller/       Register, login and profile HTTP API
│   │   │   ├── dto/              Authentication request/response contracts
│   │   │   ├── model/            UserAccount, Role and Permission
│   │   │   ├── repository/       User persistence
│   │   │   └── service/          Registration, login and JWT issuance
│   │   ├── common/error/         Consistent REST errors
│   │   ├── config/               OpenAPI configuration
│   │   ├── security/             SecurityFilterChain, JWT and security errors
│   │   └── request/
│   │       ├── comment/
│   │       ├── controller/
│   │       ├── dto/
│   │       ├── history/
│   │       ├── model/
│   │       ├── repository/
│   │       ├── security/         Ownership/permission policy
│   │       └── service/
│   ├── src/main/resources/
│   │   └── db/migration/         V1, V2 and V3 Flyway SQL
│   └── src/test/java/com/aprovexa/
│       ├── request/
│       ├── security/             End-to-end security tests
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

## V3 authorization matrix

The matrix is defined before the implementation so security behaviour is explicit and testable.

| Operation | USER | MANAGER | ADMIN |
| --- | --- | --- | --- |
| Register / login | Public | Public | Public |
| View own profile | ✅ | ✅ | ✅ |
| Create request | ✅ own identity | ✅ own identity | ✅ own identity |
| Read own request | ✅ | ✅ | ✅ |
| Read another user's request | ❌ | ✅ | ✅ |
| List requests | Own only | All | All |
| Edit/delete CREATED request | Own only | Own only | Any |
| Submit/cancel unresolved request | Own only | Own only | Any |
| Approve/reject IN_REVIEW | ❌ | ✅ | ✅ |
| Read history | Own only | All | All |
| Add/read comments | Own only | All | All |

A valid operation performed without sufficient role/ownership returns `403`. Missing or invalid authentication returns `401`.

## Permission model

Roles map to explicit permissions rather than relying only on role names. JWTs contain both the role and the derived permission list.

Representative permissions include:

```text
REQUEST_CREATE
REQUEST_READ_OWN
REQUEST_READ_ALL
REQUEST_UPDATE_OWN
REQUEST_DELETE_OWN
REQUEST_SUBMIT_OWN
REQUEST_CANCEL_OWN
REQUEST_REVIEW
REQUEST_MANAGE_ALL
REQUEST_COMMENT_OWN
REQUEST_COMMENT_ALL
REQUEST_HISTORY_OWN
REQUEST_HISTORY_ALL
```

`USER` receives own-resource permissions. `MANAGER` additionally receives read-all, review, comment-all and history-all permissions. `ADMIN` receives every permission.

## Authentication API

### Register

```text
POST /api/v1/auth/register
```

```json
{
  "displayName": "Laura Garcia",
  "email": "laura@example.com",
  "password": "ChangeMe123!"
}
```

Registration always creates a `USER`. A public client cannot self-register as `MANAGER` or `ADMIN`.

The response never contains the password or password hash.

### Login

```text
POST /api/v1/auth/login
```

```json
{
  "email": "laura@example.com",
  "password": "ChangeMe123!"
}
```

Successful authentication returns:

```text
accessToken
tokenType = Bearer
expiresAt
user
```

### Profile

```text
GET /api/v1/auth/me
Authorization: Bearer <token>
```

## JWT design

Tokens are signed with HMAC SHA-256. The secret is never stored in source control.

Claims include:

```text
iss          token issuer
sub          normalized user email
userId       persisted user id
name         display name
role         USER / MANAGER / ADMIN
permissions  effective permission names
iat          issued-at instant
exp          expiration instant
```

The resource server verifies the signature, issuer and expiration before creating the Spring Security `Authentication`.

V3 deliberately uses short-lived stateless access tokens without refresh tokens. Role changes therefore require a new login before a client receives updated claims.

## Password security

Passwords are hashed with `BCryptPasswordEncoder`. Plain passwords are used only during registration/login validation and are never stored or returned by the API.

Database constraints require the stored hash to have a valid BCrypt-sized representation.

## Identity is no longer client-controlled

V2 temporarily accepted actor/author/requester strings because authentication did not yet exist. V3 removes those fields from client input.

### Create request

V2 client supplied:

```text
requester
```

V3 derives the owner from the JWT `sub` claim.

### Lifecycle transitions

V2 supplied:

```json
{"actor":"Laura"}
```

V3 transition endpoints have no actor body. `changed_by` is the authenticated email.

### Comments

V2 supplied `author`. V3 accepts only comment `content`; `author` is the authenticated email.

This prevents clients from impersonating another user in business or audit data.

## Request API under V3

Base path remains:

```text
/api/v1/requests
```

All request endpoints require a Bearer token.

| Method | Endpoint | V3 authorization |
| --- | --- | --- |
| `POST` | `/api/v1/requests` | Authenticated; owner derived from JWT |
| `GET` | `/api/v1/requests/{id}` | Owner, MANAGER or ADMIN |
| `GET` | `/api/v1/requests` | USER own only; MANAGER/ADMIN all |
| `PUT` | `/api/v1/requests/{id}` | Owner or ADMIN; still only CREATED |
| `DELETE` | `/api/v1/requests/{id}` | Owner or ADMIN; still only CREATED |
| `POST` | `/api/v1/requests/{id}/submit` | Owner or ADMIN |
| `POST` | `/api/v1/requests/{id}/approve` | MANAGER or ADMIN |
| `POST` | `/api/v1/requests/{id}/reject` | MANAGER or ADMIN |
| `POST` | `/api/v1/requests/{id}/cancel` | Owner or ADMIN |
| `POST` | `/api/v1/requests/{id}/comments` | Owner, MANAGER or ADMIN |
| `GET` | `/api/v1/requests/{id}/comments` | Owner, MANAGER or ADMIN |
| `GET` | `/api/v1/requests/{id}/history` | Owner, MANAGER or ADMIN |

Domain lifecycle rules from V1/V2 remain unchanged. Security does not replace `409 Conflict`: it runs before or alongside the domain rule.

## SecurityFilterChain

V3 is stateless:

```text
HTTP request
   ↓
BearerTokenAuthenticationFilter
   ↓
JWT signature / issuer / expiration validation
   ↓
JWT claims -> GrantedAuthority
   ↓
endpoint permission checks
   ↓
Controller
   ↓
RequestService
   ↓
ownership / permission policy
   ↓
domain lifecycle rules
```

CSRF is disabled because the API uses stateless Bearer tokens rather than cookie-based server sessions.

Approve/reject are protected twice:

1. HTTP policy requires `REQUEST_REVIEW`;
2. the application service verifies the review permission again.

Resource ownership is always enforced in the application layer after the request has been loaded.

## Normalized security errors

Unauthenticated:

```json
{
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED"
}
```

Expired or altered token:

```json
{
  "status": 401,
  "code": "INVALID_TOKEN"
}
```

Wrong login credentials:

```json
{
  "status": 401,
  "code": "AUTHENTICATION_FAILED"
}
```

Authenticated but unauthorized:

```json
{
  "status": 403,
  "code": "ACCESS_DENIED"
}
```

All errors use the same `ApiErrorResponse` shape introduced in earlier versions.

## Database migration — V3

Flyway adds:

```text
V3__security_rbac.sql
```

The migration creates `users` with:

```text
id
email
password_hash
display_name
role
created_at
updated_at
```

Database-level rules include unique normalized email, allowed roles, password-hash length, display-name length and timestamp consistency.

An ownership-oriented index is also added to `requests(requester, created_at, id)`.

## Local configuration

V3 uses the ignored root `.env` as optional local Spring configuration as well as Docker Compose configuration.

Start from:

```powershell
Copy-Item .env.example .env
```

Generate a local JWT secret using a cryptographically secure RNG:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$jwtSecret = [Convert]::ToBase64String($bytes)
(Get-Content .env) -replace '^JWT_SECRET=.*$', "JWT_SECRET=$jwtSecret" | Set-Content .env
```

The `.env` file remains ignored by Git.

Default access-token TTL:

```text
PT30M
```

## Run V3 locally

Start PostgreSQL:

```powershell
docker compose up -d postgres
docker compose ps
```

Run all tests from `backend/`:

```powershell
.\mvnw.cmd clean verify
```

Docker is required because persistence and security integration tests use PostgreSQL 17 through Testcontainers.

Start the backend from IntelliJ with Java 21 or:

```powershell
.\mvnw.cmd spring-boot:run
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

Swagger exposes an `Authorize` button for entering:

```text
Bearer <JWT>
```

## Local MANAGER test account

Public registration intentionally creates only `USER`. For local RBAC validation, register a normal manager candidate and promote it directly in the development database:

```powershell
docker compose exec postgres psql -U aprovexa -d aprovexa -c "UPDATE users SET role='MANAGER', updated_at=now() WHERE email='manager@example.com';"
```

Then log in again so the new JWT contains the MANAGER role and permissions.

This direct SQL step is a local test setup technique, not an application feature.

## V3 automated security tests

The V3 suite validates at least:

- registration stores a BCrypt hash and does not expose it;
- correct login returns a usable JWT;
- incorrect password returns normalized `401`;
- unauthenticated request returns normalized `401`;
- expired JWT returns `401`;
- altered JWT signature returns `401`;
- `USER` attempting approval returns `403`;
- `MANAGER` approval returns `200`;
- approval history records the authenticated manager;
- one `USER` cannot access another user's request;
- USER list queries are ownership-scoped;
- MANAGER list queries can see all requests;
- V1/V2 lifecycle, Flyway, audit, comment and rollback tests continue passing.

## V3 validation status

| Check | Status |
| --- | --- |
| RBAC matrix defined | ✅ Implemented and validated |
| User/Role/Permission model | ✅ Implemented and validated |
| `mvnw clean verify` | ✅ `BUILD SUCCESS` |
| Automated test suite | ✅ 38 tests, 0 failures, 0 errors, 0 skipped |
| BCrypt registration | ✅ Hash persisted; plaintext not exposed |
| Login + JWT | ✅ Valid token issued and accepted |
| JWT expired/altered tests | ✅ Automated `401 INVALID_TOKEN` coverage |
| Missing authentication | ✅ `401 AUTHENTICATION_REQUIRED` |
| USER approval -> 403 | ✅ Validated manually and automatically |
| MANAGER approval -> 200 | ✅ Validated manually and automatically |
| Approval actor audit | ✅ Authenticated manager persisted in history |
| Foreign request -> 403 | ✅ Ownership restriction validated |
| USER list scope | ✅ Own requests only |
| MANAGER list scope | ✅ Global request visibility |
| Flyway V3 migration | ✅ Reproducible V1/V2/V3 schema validated |
| Swagger Bearer flow | ✅ Register/login/Authorize/protected endpoints validated |
| No secrets versioned | ✅ `.env` ignored; JWT secret externalized |
| GitHub Actions | ✅ Pending pull request |

## Git workflow for V3

Development branch:

```text
feature/v3-security-and-rbac
```

Planned implementation commit:

```text
feat(v3): add JWT authentication and RBAC
```

Target release tag after all gates and CI are green:

```text
v3.0.0
```
