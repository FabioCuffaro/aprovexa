# Aprovexa

Aprovexa is an internal enterprise platform for managing employee requests and approval workflows in a traceable, auditable way.

The project is being developed incrementally. Each version introduces a controlled technical or functional capability and is validated before the next version begins.

## Current version

### V0 — Foundation (`v0.1.0`)

V0 establishes the technical baseline of the repository. It intentionally contains **no business logic** yet.

The objective of this version is to make the project reproducible from a clean clone and to validate the basic development environment before introducing the request domain.

## Technology baseline

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Backend | Spring Boot 4.1.1 |
| Build | Maven Wrapper |
| Local database | PostgreSQL 17 |
| Local infrastructure | Docker Compose |
| CI | GitHub Actions |

Persistence integration, security, Kafka, Angular, batch processing and distributed services will be introduced in later versions instead of being added prematurely.

## Repository structure

```text
aprovexa/
├── backend/                 Spring Boot backend
├── frontend/                Reserved for the Angular client
├── infrastructure/          Infrastructure assets as the project evolves
├── scripts/                 Reusable project scripts
├── .github/workflows/       Continuous integration workflows
├── .env.example             Local environment variable template
├── docker-compose.yml       Local PostgreSQL infrastructure
└── README.md                Public project documentation
```

Internal working documentation is intentionally kept outside Git versioning.

## V0 implementation

### 1. Backend initialization

The backend was initialized with Spring Initializr using:

- Maven
- Java
- Java 21
- Spring Boot 4.1.1
- Group: `com.aprovexa`
- Artifact: `backend`
- Packaging: `jar`
- Spring Web MVC

The generated Maven Wrapper is committed so the project does not depend on a globally installed Maven version.

The backend version for this foundation is:

```text
0.1.0-SNAPSHOT
```

### 2. Repository foundation

The initial monorepo structure was prepared for the planned evolution of the platform:

- `backend/` contains the current Spring Boot application.
- `frontend/` is reserved for the Angular client that will be introduced later.
- `infrastructure/` is reserved for infrastructure-specific assets.
- `scripts/` is reserved for reusable local or CI scripts.
- `.github/workflows/` contains repository automation.

No future framework or service has been initialized merely to fill these directories.

### 3. Local configuration

A `.env.example` file documents the variables required by local infrastructure without committing real credentials.

Create the local file with:

**Windows PowerShell**

```powershell
Copy-Item .env.example .env
```

**Linux / macOS / Git Bash**

```bash
cp .env.example .env
```

The real `.env` file is ignored by Git.

### 4. PostgreSQL with Docker Compose

V0 provides a local PostgreSQL 17 container with:

- dedicated database and user configured through environment variables;
- persistent Docker volume;
- explicit healthcheck using `pg_isready`;
- configurable host port.

At this stage the Spring Boot application **does not connect to PostgreSQL yet**. Database integration belongs to the persistence phase of the project.

### 5. Continuous integration

A minimal GitHub Actions workflow validates the backend on pull requests and on pushes to `main` that affect backend or workflow files.

The workflow:

1. checks out the repository;
2. installs Temurin Java 21;
3. enables the Maven dependency cache;
4. ensures the Maven Wrapper is executable on the Linux runner;
5. runs `./mvnw --batch-mode clean verify` from `backend/`.

This gives the project a reproducible build gate from the first version.

## Local validation performed for V0

The following checks were executed locally before preparing the version for GitHub.

### Java runtime

```powershell
java -version
```

**Expected:** Java 21.  
**Status:** validated locally.

### Maven build and tests

From `backend/`:

```powershell
.\mvnw.cmd clean verify
```

**Expected:** Maven finishes with `BUILD SUCCESS`.  
**Status:** validated locally.

### Spring Boot startup

From IntelliJ IDEA or from `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

**Expected:** the Spring application context starts successfully and embedded Tomcat listens on port `8080`.  
**Status:** validated locally.

V0 does not expose business endpoints, so receiving `404` at `/` is expected at this stage.

### Docker Compose configuration

From the repository root:

```powershell
docker compose config
```

**Expected:** Compose resolves the configuration without YAML or environment interpolation errors.  
**Status:** validated locally.

### PostgreSQL startup

```powershell
docker compose up -d postgres
docker compose ps
```

**Expected:** `aprovexa-postgres` reaches a healthy state.  
**Status:** validated locally.

Direct database readiness check:

```powershell
docker compose exec postgres pg_isready -U aprovexa -d aprovexa
```

**Expected:** PostgreSQL reports `accepting connections`.  
**Status:** validated locally.

Stop the local infrastructure without deleting the volume:

```powershell
docker compose down
```

## Repository hygiene checks

Before every commit, the repository is checked for local files, generated artifacts and credentials.

Useful commands:

```powershell
git status
git diff
git diff --cached --check
git diff --cached --name-only
```

The following must never be committed:

- `.env`
- `.idea/`
- `backend/target/`
- local/internal project documentation
- credentials, tokens or secrets

## Git workflow for V0

Development branch:

```text
feature/v0-foundation
```

Foundation commit:

```text
chore(v0): establish project foundation
```

Version tag after the pull request is validated and merged:

```text
v0.1.0
```

The repository history is used to make the evolution of Aprovexa explicit: each version is implemented, tested and reviewed independently before moving to the next one.

## V0 acceptance status

| Check | Status |
| --- | --- |
| Java 21 available | ✅ Local validation completed |
| Maven Wrapper build | ✅ Local validation completed |
| Spring Boot startup | ✅ Local validation completed |
| Docker Compose configuration | ✅ Local validation completed |
| PostgreSQL health | ✅ Local validation completed |
| No business logic introduced | ✅ |
| Local/internal documentation excluded from Git | ✅ |
| GitHub Actions CI | ✅ `Backend CI / verify` passed on the V0 pull request |
| Tag `v0.1.0` | ⏳ Pending CI and merge |

## Next milestone

After V0 is fully validated in GitHub, development can proceed to **V1 — Request Management REST API**, where the first real domain behaviour will be implemented.
