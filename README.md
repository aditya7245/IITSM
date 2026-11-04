# I-ITSM: Intelligent IT Service Management & Incident Resolution System

CS F213 OOP course project. Employees report IT incidents; the system prioritizes them, assigns a technician, tracks SLA deadlines, sends notifications and keeps an audit trail.

**Stack:** Java 21 · Spring Boot 3.3 (layered monolith) · PostgreSQL 16 · Flyway · Spring Data JPA · Spring Security + JWT · React (planned)

> **Status:** this is the foundation scaffold. Run `mvn verify` in `backend/` to confirm everything builds before you start adding features.

## Contents
1. [Quick start](#quick-start)
2. [The big picture](#1-the-big-picture)
3. [Folder structure](#2-folder-structure)
4. [Layered architecture](#3-the-layered-architecture-the-most-important-idea)
5. [The database](#4-the-database)
6. [The user hierarchy](#5-the-user-hierarchy-inheritance-explained)
7. [Security](#6-security-step-by-step)
8. [Configuration and profiles](#7-configuration-and-profiles)
9. [Docker Compose](#8-docker-compose)
10. [Error handling](#9-error-handling)
11. [The test](#10-the-test)
12. [Design patterns](#11-design-patterns-visible-so-far)
13. [Repo conventions](#12-working-in-the-repo-conventions)
14. [Not built yet](#13-what-does-not-exist-yet)
15. [Glossary](#14-glossary)

---

## Quick start

**You need:** Java 21, Maven, Docker Desktop, Git.

```bash
# 1. Start the database and fake mail server (repo root)
docker compose up -d

# 2. Run the backend
cd backend
mvn spring-boot:run

# 3. Run the tests (Docker must be running)
mvn verify
```

**Troubleshooting `mvn verify`:** if the test fails with "Could not find a valid Docker environment" and a `400` error, Docker Desktop is newer than the Testcontainers client. The `pom.xml` already pins `testcontainers.version` to 1.21.4. If it still fails, run once in PowerShell: `"api.version=1.44" | Out-File -Encoding ascii "$HOME\.docker-java.properties"`. Docker Desktop must be running.

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health
- Mailpit (caught emails): http://localhost:8025

**Try the login:** `POST http://localhost:8080/api/auth/login`

```json
{ "email": "admin@iitsm.local", "password": "Password@123" }
```

You should get a token back. That proves the database, migrations, user mapping and security all work together.

**Demo users** (dev profile only; password `Password@123`, all `@iitsm.local`):

| Email prefix | Role |
|---|---|
| admin | Administrator |
| manager | IT Manager |
| lead.app | Team Lead |
| tech1, tech2, tech3 | Technician |
| emp1, emp2 | Employee |

---

## 1. The big picture

The scaffold is the **foundation only**. It contains:

- a PostgreSQL database with all tables already designed,
- a Spring Boot backend that starts, connects to the database, and logs a user in,
- the user class hierarchy (Employee, Technician, and so on),
- the error-handling and security plumbing later features will use.

| Piece | Technology | What it does |
|---|---|---|
| Language | Java 21 | The backend language |
| Framework | Spring Boot 3.3 | Starts the web server and wires our classes together |
| Database | PostgreSQL 16 | Stores all data |
| Schema management | Flyway | Creates and updates tables from SQL files |
| Database access | Spring Data JPA (Hibernate) | Work with Java objects instead of writing all the SQL |
| Security | Spring Security + JWT + BCrypt | Login, tokens, password hashing |
| Local email testing | Mailpit | Fake mail server that catches emails |
| Containers | Docker Compose | Runs Postgres and Mailpit with one command |
| API docs | springdoc (Swagger UI) | Auto-generated page to try the API |
| Tests | JUnit 5 + Testcontainers | Tests against a real throwaway Postgres |

## 2. Folder structure

```
iitsm/
├── docker-compose.yml        Postgres + Mailpit
├── .env.example              Template for local settings
├── docs/                     Reports, UML, ER diagrams
├── frontend/                 React app (empty for now)
└── backend/
    ├── pom.xml               Maven dependencies and build config
    └── src/
        ├── main/
        │   ├── java/com/iitsm/
        │   │   ├── IitsmApplication.java
        │   │   ├── config/  security/  controller/  service/
        │   │   ├── repository/  dto/  observer/  strategy/  exception/
        │   │   └── domain/  (common, user, incident, sla, notification, audit)
        │   └── resources/
        │       ├── application.yml, application-dev.yml, application-prod.yml
        │       └── db/migration/ (V1, V2)   db/dev/ (V3, dev only)
        └── test/java/com/iitsm/IitsmApplicationTests.java
```

Folders with only a `.gitkeep` are intentionally empty: they reserve the place where future code goes.

| Package | Purpose | Contains today |
|---|---|---|
| `controller` | Receives HTTP requests, returns responses. Almost no logic. | `AuthController` |
| `service` | The business logic (the rules of the system). | `AuthService` |
| `repository` | Reads and writes the database. | `UserRepository` |
| `dto` | Simple shapes for what goes in and out of the API. | `LoginRequest`, `LoginResponse` |
| `domain` | Core objects (users, incidents, ...) and their rules. | User hierarchy, `Team`, `BaseEntity`, `Role` |
| `config` | Settings classes. | `SecurityConfig`, `SecurityProperties` |
| `security` | Login and token machinery. | JWT service and filter, `UserPrincipal` |
| `exception` | Custom errors and how they become HTTP responses. | Full exception set |
| `observer` | (Empty) Observer pattern: audit and notification listeners. | nothing yet |
| `strategy` | (Empty) Strategy pattern: assignment, priority, similarity. | nothing yet |

## 3. The layered architecture (the most important idea)

Our backend is a **layered monolith**: one application, organized in layers where each layer only talks to the one below it.

```
Browser / React app
        │  HTTP request (JSON)
        ▼
  Controller      "What was asked? Is the input valid?"
        │
        ▼
  Service         "What are the business rules? What should happen?"
        │
        ▼
  Repository      "How do I load/save this in the database?"
        │
        ▼
  PostgreSQL
```

Each layer has one job, so you always know where code belongs:

- A rule like "a technician cannot exceed their max workload" goes in the **service** (or the domain object), never a controller.
- SQL and database details stay in **repositories**.
- Controllers stay thin: pass the request on, return the result.

This is also what the course means by good object-oriented design: each class has a clear responsibility, and a part (like the assignment algorithm) can be swapped without touching unrelated code.

### Following one request: login

1. The client sends `POST /api/auth/login` with email and password.
2. **`AuthController`** receives it. `@Valid` checks the input is well-formed; if not, a 400 is returned.
3. The controller calls **`AuthService.login`**.
4. The service asks Spring Security's `AuthenticationManager` to check the credentials.
5. Spring Security calls **`CustomUserDetailsService`**, which uses **`UserRepository`** to find the user by email.
6. Spring Security compares the typed password with the stored **BCrypt hash** (plain passwords are never compared or stored).
7. If it matches, **`JwtService`** creates a signed token with the email, user id and role, valid for 60 minutes.
8. The service returns a `LoginResponse` with the token. On failure, `InvalidCredentialsException` becomes a 401.

After logging in, the client sends `Authorization: Bearer <token>` on every request. **`JwtAuthenticationFilter`** reads it, validates it, and tells Spring Security who the user is.

## 4. The database

### Flyway: version control for the database

We never create or change tables by hand. We write numbered SQL files and **Flyway** runs them in order the first time the app starts, recording which ones already ran.

| File | Runs in | What it does |
|---|---|---|
| `V1__baseline_schema.sql` | All environments | Creates every table |
| `V2__reference_data.sql` | All environments | SLA defaults, categories, skills, config values |
| `db/dev/V3__dev_seed_data.sql` | Dev only | Demo teams, users, services |

**Rules:**

1. **Never edit a migration that has been committed and run.** Flyway stores a checksum; a changed file makes the app refuse to start on everyone else's machine. To change the schema, add a *new* file (`V4__something.sql`).
2. Version numbers must be unique. If two people both create `V4`, one renames to `V5` before merging.
3. Hibernate runs with `ddl-auto: validate`: it only *checks* that Java classes match the tables and fails at startup if not. It never changes tables.

### The tables

| Table | Purpose |
|---|---|
| `users` | Everyone who can log in (all roles in one table) |
| `teams` | Support teams; each has a lead |
| `skills`, `technician_skills` | Skills, and which technician has which at what level (1 to 5) |
| `categories` | Incident categories with a `keywords` column for auto-categorization. New categories are new rows, no code change |
| `it_services` | Services that can break, with business criticality 1 to 5 |
| `sla_policies` | Response and resolution minutes per priority (P1 to P4). No service = default; a service = override |
| `incidents` | Central table: reporter, service, impact, urgency, priority, status, assignee, SLA deadlines, timestamps |
| `incident_comments` | Investigation notes, info requests and responses |
| `audit_logs` | Append-only history. A database trigger blocks any UPDATE or DELETE |
| `notifications` | In-app notifications per user |
| `feedback` | One rating and comment per incident |
| `system_config` | Admin-changeable key/value settings, such as the active assignment strategy |

Many columns have `CHECK` constraints (impact must be 1 to 3, status must be an allowed value), so the database itself rejects bad data as a second line of defense.

## 5. The user hierarchy (inheritance, explained)

```
            User  (abstract)
   ┌──────────┼───────────────┐
Employee  Technician     Administrator
              │                │
          TeamLead         ITManager
```

- **Abstract** means you cannot create a plain `User`; you create one of the concrete types.
- `TeamLead` **is a** `Technician`; `ITManager` **is an** `Administrator`. The course rule is to use inheritance only for true "is-a" relationships.

### Single-table inheritance

All user types live in one `users` table; a `role` column says which type each row is.

| id | role | email | department | team_id | max_workload |
|---|---|---|---|---|---|
| 1 | ADMINISTRATOR | admin@... | NULL | NULL | NULL |
| 3 | EMPLOYEE | emp1@... | Finance | NULL | NULL |
| 6 | TECHNICIAN | tech1@... | NULL | 1 | 5 |

Columns that apply to only some types are NULL for the others, which is why they are nullable in the SQL.

**Why:** login must find any user by email in one query, and a single table makes that trivial. The trade-off is the NULL columns, which is a good deal here and a good point for the report.

```java
@Entity @Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", ...)
public abstract class User extends BaseEntity { ... }

@Entity @DiscriminatorValue("TECHNICIAN")
public class Technician extends User { ... }
```

Hibernate reads the `role` column and creates the right Java class, so `userRepository.findById(6)` returns an actual `Technician`.

### `getRole()`: polymorphism instead of if/else

`User` declares `public abstract Role getRole();` and each subclass returns its own role. Code never needs `if (user instanceof Technician)` to find the type; each object answers for itself.

### The `Role` enum and inherited permissions

```java
TEAM_LEAD("ROLE_TECHNICIAN", "ROLE_TEAM_LEAD")
IT_MANAGER("ROLE_ADMIN", "ROLE_IT_MANAGER")
```

A team lead *is a* technician, so it gets both authorities. Anything protected for technicians lets team leads in; likewise IT managers and admin endpoints.

### Encapsulation

Fields are `private`, with getters but no blanket setters. Changes go through methods that describe intent (`deactivate()`, `setAvailable(...)`, `requireCapacity(...)`). `Technician.requireCapacity` shows a business rule living inside the domain object: it throws `WorkloadExceededException` if the technician is full. Entities have a `protected` no-argument constructor because JPA needs one and `protected` stops accidental use.

### `BaseEntity`

Every entity extends `BaseEntity`, which provides an auto-generated `id` and automatic `createdAt` / `updatedAt` timestamps, written once instead of in every class.

## 6. Security, step by step

- **Authentication** = proving who you are (login).
- **Authorization** = what you are allowed to do (roles).
- **Stateless** = the server does not remember you between requests; your token proves it each time.

**Passwords (BCrypt):** stored only as one-way hashes such as `$2a$10$xT.ED...`. At login Spring hashes what you typed and compares. A leaked database would not reveal passwords.

**Tokens (JWT):** a signed string given after login containing your email, id, role and expiry. The server signs it with a secret key, so it cannot be forged or altered. The frontend sends it with each request.

| Class | Role |
|---|---|
| `SecurityConfig` | Which URLs are public (login, Swagger, health) and which need a token; stateless sessions; CORS |
| `JwtService` | Creates and validates tokens |
| `JwtAuthenticationFilter` | Runs before every request, reads the token, tells Spring who the user is |
| `CustomUserDetailsService` | Loads a user from the database for Spring Security |
| `UserPrincipal` | An **adapter**: converts our `User` into the `UserDetails` Spring Security understands, so domain classes do not depend on Spring Security |
| `SecurityProperties` | Typed settings from `application.yml` (secret, expiry, allowed origins) |

**CORS:** browsers block a React app on port 5173 from calling an API on port 8080 unless the API allows it. We allow only the configured origin (`http://localhost:5173` by default).

`@EnableMethodSecurity` is on, so methods can be protected like this:

```java
@PreAuthorize("hasRole('ADMIN')")
public void configureSla(...) { ... }
```

Role checks alone are not enough: an employee should only see their *own* incidents, so ownership checks will go in the service layer later.

## 7. Configuration and profiles

`${DB_HOST:localhost}` in `application.yml` means "use environment variable `DB_HOST` if set, otherwise `localhost`", so the same code runs on any machine.

| Profile | File | Difference |
|---|---|---|
| `dev` (default) | `application-dev.yml` | Also loads `db/dev`, so demo users exist. More verbose logging |
| `prod` | `application-prod.yml` | No demo data. `JWT_SECRET` must be provided or the app will not start |

Project settings under `iitsm:`:

- `iitsm.sla.time-scale`: above 1 speeds up time (at 10, a 30-minute SLA passes in 3 minutes), for demos.
- `iitsm.sla.warning-threshold`: 0.8 = "at risk" at 80% of the deadline.
- `iitsm.sla.monitor-interval-ms`: how often the background checker will run.
- `iitsm.escalation.level-timeout-minutes`: time before escalating to the next level.

**Secrets:** never commit the real JWT secret or SMTP password. Use environment variables or a local `.env` file (ignored by Git). `.env.example` is the safe template.

## 8. Docker Compose

- **postgres:** the database on port 5432 with a named volume so data survives restarts. To wipe everything: `docker compose down -v`.
- **mailpit:** accepts emails on port 1025 and shows them at http://localhost:8025. No real emails are sent.

The backend itself is not in Compose yet; run it from your IDE or `mvn spring-boot:run`.

## 9. Error handling

```
IitsmException (abstract, carries an HTTP status)
 ├── InvalidCredentialsException        401
 ├── UnauthorizedOperationException     403
 ├── IncidentNotFoundException          404
 ├── ServiceNotFoundException           404
 ├── InvalidStateTransitionException    409
 ├── DuplicateIncidentException         409
 ├── TechnicianUnavailableException     422
 ├── WorkloadExceededException          422
 └── MissingSlaConfigurationException   500
```

Any code can `throw new InvalidStateTransitionException("NEW", "CLOSED")`. One class, `GlobalExceptionHandler` (`@RestControllerAdvice`), catches every exception and returns a consistent JSON body:

```json
{ "timestamp": "...", "status": 409, "error": "Conflict",
  "message": "Cannot move incident from NEW to CLOSED", "path": "/api/incidents/5/close" }
```

It also handles validation errors (400, listing the bad fields), database failures (503) and unexpected errors (500, logged instead of leaked). Business code stays free of try/catch, and the frontend always gets errors in one shape.

## 10. The test

`IitsmApplicationTests` is a **smoke test**. It uses **Testcontainers** to start a throwaway Postgres in Docker, then:

1. starts the full application,
2. runs all Flyway migrations,
3. lets Hibernate validate that every mapped class matches its table,
4. loads the demo users and confirms each is the correct subclass (and a team lead is also a `Technician`).

If it passes, migrations, mappings and inheritance agree. Docker must be running.

## 11. Design patterns visible so far

| Pattern | Where | Status |
|---|---|---|
| Adapter | `UserPrincipal` adapts `User` to Spring Security's `UserDetails` | Present |
| Repository | Spring Data repositories such as `UserRepository` | Present (framework-provided) |
| State | Incident lifecycle | Planned |
| Strategy | Assignment, priority calculation, similarity | Planned |
| Observer | Audit logging and notifications on incident changes | Planned |
| Factory | Notification channel creation | Planned |

## 12. Working in the repo: conventions

- **Put code in the right layer.** Controller: HTTP only. Service: rules. Repository: database.
- **Never return entities from controllers.** Use DTOs so API shapes do not leak database details.
- **Schema changes only through new Flyway files.**
- **No secrets in Git.**
- **Throw our custom exceptions** for business errors.
- **Run `mvn verify` before pushing.**
- **Commit small and often** with clear messages, and pull before you push.

## 13. What does not exist yet

- Incident entity, creation, priority calculation
- The lifecycle state machine
- Technician assignment strategies
- SLA monitoring (scheduling is enabled but nothing is scheduled yet)
- Audit and notification observers; email sending
- Search, filtering, reports, dashboards
- Duplicate detection, auto-categorization, auto-escalation
- The React frontend
- Java entities for skills, categories, services, SLA policies, comments, audit logs, notifications and feedback. **The tables exist, the classes do not yet.** Hibernate only validates tables that have a matching class, so this is harmless.

## 14. Glossary

| Term | Meaning |
|---|---|
| **Annotation** | A `@` tag telling Spring or JPA how to treat a class or method (`@Entity`, `@Valid`) |
| **Bean** | An object created and managed by Spring |
| **Dependency injection** | Spring supplies the objects a class needs via its constructor |
| **Entity** | A Java class mapped to a database table |
| **JPA / Hibernate** | The standard (JPA) and the library (Hibernate) that map objects to rows |
| **Migration** | A versioned SQL file that changes the database structure |
| **DTO** | A simple object carrying data in and out of the API |
| **REST API** | Web endpoints (URLs plus HTTP methods) returning JSON |
| **JWT** | A signed token proving who the caller is |
| **BCrypt** | A one-way password hashing algorithm |
| **CORS** | A browser rule controlling which websites may call our API |
| **Profile** | A named configuration set (`dev`, `prod`) |
| **Monolith** | One application deployed as a single unit |
| **Discriminator** | The column saying which subclass a row belongs to |
| **SLA** | Service Level Agreement: response and resolution time targets |
| **Testcontainers** | Starts real Docker containers (like Postgres) during tests |
