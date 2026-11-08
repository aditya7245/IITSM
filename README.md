# I-ITSM: Intelligent IT Service Management & Incident Resolution System

CS F213 OOP course project (due 22 Nov 2026). Employees report IT incidents; the system prioritizes them, assigns a technician, tracks SLA deadlines, sends notifications and keeps an audit trail.

**Stack:** Java 21 · Spring Boot 3.3 (layered monolith) · PostgreSQL 16 · Flyway · Spring Data JPA · Spring Security + JWT · React (planned)

**Contents:** [1. How to run everything](#1-how-to-run-everything) · [2. Build checklist](#2-build-checklist) · [3. Scaffold guide](#3-scaffold-guide-what-is-in-the-repo)

---

# 1. How to run everything

**Needs:** Java 21, Maven, Docker Desktop (running), Git.

```bash
git clone https://github.com/aditya7245/IITSM.git
cd IITSM
docker compose up -d                  # Postgres + Mailpit
cd backend
mvn spring-boot:run                   # API on http://localhost:8080
```

| What | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| Mailpit (caught emails) | http://localhost:8025 |

**Check it works** (PowerShell):

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body '{"email":"admin@iitsm.local","password":"Password@123"}'
```

You should get a `token` back. Demo users (dev profile, password `Password@123`): `admin@`, `manager@`, `lead.app@`, `tech1@`, `tech2@`, `tech3@`, `emp1@`, `emp2@` + `iitsm.local`.

**Run tests** (Docker must be running): `cd backend && mvn verify`

**Stop / reset:** `docker compose down` (keep data) · `docker compose down -v` (wipe the database)

**If something breaks**

| Problem | Fix |
|---|---|
| Test error "Could not find a valid Docker environment" | Start Docker Desktop. If it still fails, run once: `"api.version=1.44" \| Out-File -Encoding ascii "$HOME\.docker-java.properties"` |
| "Connection refused" to the database | Run `docker compose up -d` and wait a few seconds |
| Flyway "checksum mismatch" | A migration was edited. Don't edit V1 to V3; add a new `V<n>` file. Locally: `docker compose down -v` and restart |

---

# 2. Build checklist

Everything left to build, in dependency order: **every item only depends on items above it**. Each item has three parts: **In plain words** (what and why, for humans), **Details for Claude** (exact names, rules and decisions, so nothing is ambiguous), and **Done when** (how to know it is finished). To use an item with Claude, give it the item, the "Rules for every item" section, and access to the repo. Tick the box (`[x]`) in this file when an item is merged.

### Status at a glance

**Already done in the repo (the scaffold):** Spring Boot project and Maven setup; Docker Compose (Postgres and Mailpit); configuration and dev/prod profiles; Flyway migrations V1 (full schema), V2 (reference data) and V3 (dev demo users); the user class hierarchy (User, Employee, Technician, TeamLead, Administrator, ITManager, Team); JWT login (`POST /api/auth/login`), BCrypt passwords and role-based security setup; the base exception hierarchy and global error handler; a smoke test; this README.

**Checklist items done:** 21 of 53 (all of Phase 1 and Phase 2, plus 3.1 to 3.3).

| Phase | Items | Done |
|---|---|---|
| 1 Foundations | 1.1 to 1.10 | all |
| 2 Pure business rules | 2.1 to 2.8 | all |
| 3 Events, audit, notifications | 3.1 to 3.4 | 3.1 to 3.3 |
| 4 Services | 4.1 to 4.10 | none |
| 5 REST API | 5.1 to 5.7 | none |
| 6 Demo and tests | 6.1 to 6.3 | none |
| 7 Frontend | 7.1 to 7.7 | none |
| 8 Innovation and documents | 8.1 to 8.4 | none |

### Rules for every item

1. **Package root** is `com.iitsm`. No Lombok. Entities follow the style of `User`: private fields, a `protected` no-arg constructor, intent methods (`assignTo(...)`) instead of setters.
2. **Which base class an entity extends** is decided by its table's columns, because Hibernate runs in `validate` mode and fails startup on any mismatch:

| Tables | Entity base |
|---|---|
| `users`, `teams`, `incidents` | `BaseEntity` (id, created_at, updated_at) |
| `incident_comments`, `audit_logs`, `notifications`, `feedback` | `CreatedOnlyEntity` (id, created_at), done in 1.1 |
| `skills`, `categories`, `it_services`, `sla_policies`, `system_config`, `technician_skills` | no base class; declare own `@Id`, no timestamps |

3. **Type mapping:** SMALLINT -> `short`, INT -> `int`, BIGINT -> `Long`, TIMESTAMPTZ -> `Instant`, VARCHAR enums -> `@Enumerated(EnumType.STRING)`. Mapping a SMALLINT column to `int` fails validation.
4. **Time:** inject `java.time.Clock` (item 1.3). Business code never calls `Instant.now()`.
5. **Errors:** throw subclasses of `IitsmException` (add new ones in `exception/` with an HTTP status). Controllers return DTO records, never entities.
6. **Tests:** every item ships tests and `mvn verify` must pass. Pure-logic items use plain JUnit 5 (no Spring). Database items extend `AbstractIntegrationTest` (item 1.3).
7. **Migrations:** never edit V1 to V3. The only planned new migration is V4 (item 1.4); any other schema change is V5, V6, and so on, and whoever adds it checks the repo for the next free number first.
8. **Git:** one branch per item (`feature/<id>-<name>`), pull request into `main`, `mvn verify` green before pushing.
9. **Terminology:** *open* = status is not CLOSED, CANCELLED or DUPLICATE. *Active workload* = status is ASSIGNED, ACKNOWLEDGED, IN_PROGRESS or PENDING_INFO. *Admin* = role ADMINISTRATOR or IT_MANAGER. *Staff* = technician (including team lead) or admin.

---

### Phase 1: Foundations (types, config, entities, repositories)

- [x] **1.1 Base entity classes.** *Status: done.*
  **In plain words:** Some database tables record only when a row was created, others also when it was last changed. We need two small parent classes so each table's Java class matches its table exactly. We also let an incident's creation time be set by us, because the SLA timer starts from it.
  **Details for Claude:** `BaseEntity` gains `protected void initCreatedAt(Instant)` and `onCreate()` keeps a `createdAt` that is already set. New `CreatedOnlyEntity` (`@MappedSuperclass`: id, `createdAt` set in `@PrePersist` unless preset, same `initCreatedAt`, same equals/hashCode style) in `domain/common`.
  *Done when:* `BaseEntitiesTest` passes and the smoke test still passes.

- [x] **1.2 Enums.** *Status: done.*
  **In plain words:** Fixed lists of values the whole app uses (incident statuses, priorities, SLA states, comment types), with small helper methods such as "is this incident still open?".
  **Details for Claude:** In `domain/incident`: `IncidentStatus` (NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, PENDING_INFO, RESOLVED, VERIFIED, CLOSED, CANCELLED, DUPLICATE) with `isTerminal()` (CLOSED, CANCELLED, DUPLICATE), `isOpen()` (not terminal) and `countsTowardWorkload()` (ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, PENDING_INFO). `Priority` (P1, P2, P3, P4) with `bump()` returning the next more urgent tier (P4 -> P3 -> P2 -> P1; P1 stays P1). `SlaStatus` (ON_TRACK, AT_RISK, VIOLATED, declared in increasing severity). `CommentType` (NOTE, INFO_REQUEST, INFO_RESPONSE).
  *Done when:* unit tests cover `bump()` and the three status helpers.

- [x] **1.3 Time and test infrastructure.** *Status: done.*
  **In plain words:** Tests need to control the clock (to check SLA deadlines without waiting) and share one database setup. This item builds both, plus a few error types and settings classes later items need.
  **Details for Claude:** A `Clock` bean (`Clock.systemUTC()`) in `config/`. `SlaProperties(double timeScale, double warningThreshold, long monitorIntervalMs)` bound to `iitsm.sla`, and `EscalationProperties(long levelTimeoutMinutes)` bound to `iitsm.escalation` (records, picked up by the existing `@ConfigurationPropertiesScan`). `InvalidInputException` (HTTP 400) and `DuplicateResourceException` (HTTP 409) in `exception/`. In `src/test`: `MutableClock` (a `Clock` whose time tests can set and advance), a `@TestConfiguration` providing it as `@Primary`, and `AbstractIntegrationTest` (Postgres Testcontainer with `@ServiceConnection`, `@ActiveProfiles("dev")`, `@SpringBootTest`) refactored out of `IitsmApplicationTests`.
  *Done when:* `IitsmApplicationTests` extends `AbstractIntegrationTest` and passes; a test advances `MutableClock` and reads it back.

- [x] **1.4 Migration V4.** *Status: done.*
  **In plain words:** Incidents need readable numbers like INC1001, generated safely by the database. We also loosen the duplicate-detection setting so it actually catches similar short titles.
  **Details for Claude:** `V4__incident_number_sequence.sql`: `CREATE SEQUENCE incident_number_seq START 1001;` and `UPDATE system_config SET config_value = '0.3' WHERE config_key = 'duplicate.threshold';` (0.5 was too strict for short titles). Add `IncidentNumberGenerator.next()` that runs `select nextval('incident_number_seq')` through `JdbcTemplate` and returns `"INC" + n`.
  *Done when:* an integration test shows two calls return `INC1001` and `INC1002`, and the threshold row reads `0.3`.

- [x] **1.5 Reference entities and repositories.** *Status: done.*
  **In plain words:** Java classes for the lookup data: skills, which technician has which skill, incident categories and the IT services that can break.
  **Details for Claude:** `Skill`; `TechnicianSkill` (`@EmbeddedId TechnicianSkillId(technicianId, skillId)`, `short proficiency`); `Category` (name, description, `keywords` string, `requiredSkill` many-to-one nullable, `active`); `ITService` (name, description, `short businessCriticality`, `active`). Repositories for each, plus `CategoryRepository.findByActiveTrue()`, `ITServiceRepository.findByActiveTrue()`, and `TechnicianSkillRepository.findByTechnicianIdIn(Collection<Long>)`.
  *Done when:* an integration test loads the seeded categories (9) and services (4) and the seeded skills of `tech1@iitsm.local`.

- [x] **1.6 System configuration.** *Status: done.*
  **In plain words:** A small settings table the admin can change at runtime (for example which assignment strategy is active), and a helper to read it.
  **Details for Claude:** `SystemConfig` entity (String id `configKey`, `configValue`, description). `ConfigKeys` constants for the seeded keys (`assignment.strategy`, `duplicate.threshold`, `duplicate.window.hours`, `priority.bump.users`). `ConfigService` with `getString(key, default)`, `getInt(key, default)`, `getDouble(key, default)` and `set(key, value)`.
  *Done when:* tests read each seeded key with the correct type and a missing key returns the default.

- [x] **1.7 SLA policy lookup.** *Status: done.*
  **In plain words:** Given a priority and a service, find out how many minutes the team has to respond and resolve. A service can override the default times.
  **Details for Claude:** `SlaPolicy` entity (`Priority priority`, nullable `ITService service`, `int responseMinutes`, `int resolutionMinutes`). `SlaPolicyService.findPolicy(Priority, ITService)`: a policy for that service and priority wins; otherwise the default (service null) for the priority; otherwise throw `MissingSlaConfigurationException`.
  *Done when:* tests cover the service override, the default fallback and the exception (use a service-specific row inserted by the test).

- [x] **1.8 Incident entity and repository.** *Status: done.*
  **In plain words:** The central object of the whole system: one reported problem, with everything we track about it. This item only stores the data; behaviour comes later.
  **Details for Claude:** `Incident extends BaseEntity` mapping every column of `incidents`: `incidentNumber`, `title`, `description`, `reporter` (`User`, lazy), `category`, `service`, `assignee` (`Technician`, lazy, nullable), `short impact`, `short urgency`, `int affectedUsers`, `Integer priorityScore`, `Priority priority` (nullable), `IncidentStatus status` (default NEW), `SlaStatus slaStatus` (default ON_TRACK), `Instant responseDeadline`, `resolutionDeadline`, `slaPausedAt`, `long slaPausedSeconds`, `short escalationLevel`, `duplicateOf` (self reference), `resolution`, `respondedAt`, `resolvedAt`, `closedAt`. One public constructor: `Incident(String number, String title, String description, User reporter, ITService service, Category category, short impact, short urgency, int affectedUsers, Instant createdAt)` that calls `initCreatedAt`. Getters only; behaviour methods come in 2.4. `IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident>` with `Optional<Incident> findByIncidentNumber(String)`.
  *Done when:* a test saves and reloads an incident and Hibernate validation passes.

- [x] **1.9 Supporting entities.** *Status: done.*
  **In plain words:** Classes for comments, feedback, the audit history and notifications.
  **Details for Claude:** `IncidentComment` (incident, author, `CommentType type`, body), `Feedback` (one-to-one incident, `short rating`, comments), `AuditLog` (incident nullable, user nullable, `action` string, `previousState`, `newState` strings, description; constructor only, no setters), `Notification` (user, incident nullable, `eventType` string, `message`, boolean field mapped to column `is_read` with `markRead()`), all extending `CreatedOnlyEntity`. Repositories for each.
  *Done when:* a test saves one of each and reloads it.

- [x] **1.10 Repository queries needed later.** *Status: done.*
  **In plain words:** A few database questions later features will ask: who are exactly the administrators, which technicians are free, how many active incidents does a technician have.
  **Details for Claude:** `UserRepository`: `@Query("select u from User u where type(u) = :type") List<User> findAllByExactType(@Param("type") Class<? extends User> type)` (returns only the exact type, so administrators exclude IT managers; the escalation chain needs this). `TechnicianRepository extends JpaRepository<Technician, Long>` with `findByActiveTrueAndAvailableTrue()`. `IncidentRepository`: `long countByAssigneeIdAndStatusIn(Long, Collection<IncidentStatus>)`, `List<Incident> findByStatusNotIn(Collection<IncidentStatus>)`, `List<Incident> findByServiceIdAndStatusNotInAndCreatedAtAfter(Long, Collection<IncidentStatus>, Instant)`.
  *Done when:* integration tests confirm `findAllByExactType(Administrator.class)` returns the admin only and `ITManager.class` returns the manager only.

### Phase 2: Pure business rules (plain JUnit, no database)

- [x] **2.1 Priority calculator (Strategy).** *Status: done.*
  **In plain words:** The rule that turns "how bad, how urgent, how important is the service" into P1 to P4, automatically.
  **Details for Claude:** `PriorityCalculator` interface: `PriorityResult calculate(int impact, int urgency, int criticality, int affectedUsers, int bumpUsersThreshold)`; `record PriorityResult(int score, Priority priority)`. `ScoreBasedPriorityCalculator`: score = impact (1 to 3) x urgency (1 to 3) x criticality (1 to 5); score >= 27 is P1, 12 to 26 is P2, 5 to 11 is P3, under 5 is P4. Then if `affectedUsers >= bumpUsersThreshold`, apply `bump()`. Out-of-range inputs throw `InvalidInputException`.
  *Done when:* boundary tests pass for scores 27, 26, 12, 11, 5, 4, 1, 45, the bump at 99 versus 100 users, and P1 staying P1 after a bump.

- [x] **2.2 Incident state machine (State pattern).** *Status: done.*
  **In plain words:** The rulebook for an incident's life: which status it may move to next. It stops nonsense like jumping from NEW straight to CLOSED.
  **Details for Claude:** In `domain/incident/state`: `IncidentState` interface (`IncidentStatus status()`, `Set<IncidentStatus> allowedNext()`), one class per status, `IncidentStates.of(IncidentStatus)` registry, and `IncidentStateMachine.requireTransition(from, to)` throwing `InvalidStateTransitionException`. The allowed transitions are exactly:

| From | Allowed next |
|---|---|
| NEW | CLASSIFIED, CANCELLED, DUPLICATE |
| CLASSIFIED | ASSIGNED, CANCELLED, DUPLICATE |
| ASSIGNED | ACKNOWLEDGED, CANCELLED, DUPLICATE |
| ACKNOWLEDGED | IN_PROGRESS, DUPLICATE |
| IN_PROGRESS | PENDING_INFO, RESOLVED |
| PENDING_INFO | IN_PROGRESS |
| RESOLVED | VERIFIED, IN_PROGRESS |
| VERIFIED | CLOSED |
| CLOSED, CANCELLED, DUPLICATE | none |

  *Done when:* one test iterates all 11 x 11 pairs and asserts exactly the table above is allowed; `NEW -> CLOSED` throws.

- [x] **2.3 SLA evaluator.** *Status: done.*
  **In plain words:** The stopwatch logic. It works out the response and resolution deadlines, and says whether an incident is on track, close to its deadline (80%), or late. Time spent waiting for the user does not count.
  **Details for Claude:** A pure `SlaEvaluator` class. `Deadlines computeDeadlines(Instant start, int responseMinutes, int resolutionMinutes, double timeScale, long pausedSeconds)`: each window is `minutes * 60 / timeScale` seconds; deadline = start + window + pausedSeconds. `SlaStatus evaluate(...)` takes `createdAt`, `responseDeadline`, `resolutionDeadline`, `respondedAt`, `resolvedAt`, `slaPausedAt`, `slaPausedSeconds`, `now`, `warningThreshold`. Rules: `effectiveNow = slaPausedAt != null ? slaPausedAt : now`. For each phase compute `ref` = `respondedAt` (response phase) or `resolvedAt` (resolution phase) if set, else `effectiveNow`. Phase status is VIOLATED if `ref` is after the deadline; else AT_RISK if the paused-excluded elapsed time `(ref - createdAt - pausedSeconds)` is at least `warningThreshold x (deadline - createdAt - pausedSeconds)` **and the phase is not yet completed**; else ON_TRACK. Overall = the worse of the two phases.
  *Done when:* tests cover exactly 80% (AT_RISK), just under 80% (ON_TRACK), exactly at the deadline (not violated), 1 second late (violated), a response met in time while resolution is pending, a paused incident not drifting, and a time-scale of 10.

- [x] **2.4 Incident behaviour methods.** *Status: done.*
  **In plain words:** Teach the Incident object to do things itself (acknowledge, resolve, pause the SLA clock, and so on), always checking the rulebook from 2.2 first.
  **Details for Claude:** Add these to `Incident`, each calling `IncidentStateMachine.requireTransition` first and taking `Instant now` where time matters: `classify(Priority, int score, Instant responseDeadline, Instant resolutionDeadline)` (NEW -> CLASSIFIED); `assignTo(Technician)` (CLASSIFIED -> ASSIGNED; also allowed while already ASSIGNED, as a reassignment, with no status change); `acknowledge(now)` (ASSIGNED -> ACKNOWLEDGED, sets `respondedAt`); `startProgress()`; `requestInfo(now)` (IN_PROGRESS -> PENDING_INFO, sets `slaPausedAt`); `receiveInfo(now)` (PENDING_INFO -> IN_PROGRESS; adds the pause length to both deadlines and to `slaPausedSeconds`, clears `slaPausedAt`); `resolve(String text, now)` (non-blank text, sets `resolution` and `resolvedAt`); `verify()`; `reopen()` (RESOLVED -> IN_PROGRESS, clears `resolution` and `resolvedAt`); `close(now)`; `cancel()`; `markDuplicateOf(Incident original)` (original must be a different incident); `changePriority(Priority, int score, Instant responseDeadline, Instant resolutionDeadline)` (allowed only before RESOLVED); `raiseSlaStatus(SlaStatus)` (never lowers severity); `incrementEscalationLevel()`.
  *Done when:* unit tests walk the full happy path, the pause/resume deadline shift, reopen, and one invalid call per method.

- [x] **2.5 Text tokenizer.** *Status: done.*
  **In plain words:** Breaks a sentence into clean lowercase keywords, ignoring filler words like "the" and "is". The smart features that compare text all rely on it.
  **Details for Claude:** `TextTokenizer.tokens(String)` returns a `Set<String>`: lower-case, split on non-letter/non-digit characters, drop tokens shorter than 2 characters, drop these stop words: the, a, an, is, are, was, were, be, been, to, of, in, on, at, for, and, or, with, my, our, we, i, it, its, this, that, has, have, had, not, no, from, by, as, can, cannot, please. No stemming.
  *Done when:* tests cover punctuation, case, stop words, null and empty input.

- [x] **2.6 Category suggester (intelligent feature 1).** *Status: done.*
  **In plain words:** Reads the problem description and suggests the most likely category (for example, "payment" points to Application). Smart feature number one.
  **Details for Claude:** `CategorySuggester.suggest(String text, List<Category> categories)` returns a list of `CategoryScore(Category, int hits)` sorted by hits descending. A category's `keywords` are comma-separated single words; `hits` is the number of its keywords present in `TextTokenizer.tokens(text)`. Inactive categories are skipped; categories with zero hits are omitted; ties go to the lower id.
  *Done when:* tests use the seeded keywords: "payment transaction failing" ranks Application first, "cannot login password locked" ranks Authentication first, and unrelated text returns an empty list.

- [x] **2.7 Similarity strategy.** *Status: done.*
  **In plain words:** A score from 0 to 1 for how alike two texts are. Used to spot duplicate reports.
  **Details for Claude:** `SimilarityStrategy` interface (`double similarity(String a, String b)`) and `JaccardSimilarityStrategy`: |tokens(a) intersect tokens(b)| / |union|, and 0 if both sets are empty.
  *Done when:* tests cover identical text (1.0), disjoint text (0.0), a known partial overlap, and empty input.

- [x] **2.8 Assignment strategies (Strategy pattern, intelligent feature 2).** *Status: done.*
  **In plain words:** Three interchangeable ways to pick a technician: best skill match, least busy, or take turns. The admin can switch between them without changing code.
  **Details for Claude:** `record TechnicianCandidate(Technician technician, int activeIncidents, int proficiency)` (proficiency is the technician's level in the category's required skill, 0 if they lack it or the category has none). `AssignmentStrategy`: `String name()`, `List<TechnicianCandidate> rank(List<TechnicianCandidate> eligible, boolean skillRequired)` (best first), and a default no-op `void recordAssigned(Technician t)`. Names: `SKILL_BASED`, `LOAD_BASED`, `ROUND_ROBIN`. `SkillBasedStrategy`: if `skillRequired`, drop candidates with proficiency 0; order by proficiency desc, then activeIncidents asc, then experienceYears desc, then id asc. `LoadBasedStrategy`: activeIncidents asc, then experienceYears desc, then id asc. `RoundRobinStrategy`: order by id, rotated to start after the id last passed to `recordAssigned` (initially start at the lowest id); the last id is kept in memory. An empty result means nobody is suitable; there is no fallback between strategies.
  *Done when:* tests cover each ordering rule and tie-breaker, the empty result when nobody has the skill, and round-robin wrapping around.

### Phase 3: Events, audit and notifications

- [x] **3.1 Event model and observer plumbing (Observer pattern).** *Status: done.*
  **In plain words:** Whenever something happens to an incident, the system announces it, and independent listeners (audit log, notifications) react. This builds the announcement and the listener mechanism.
  **Details for Claude:** `IncidentEventType` enum: CREATED, CLASSIFIED, PRIORITY_CHANGED, ASSIGNED, ASSIGNMENT_FAILED, ACKNOWLEDGED, INVESTIGATION_STARTED, INFO_REQUESTED, INFO_RECEIVED, RESOLVED, REOPENED, VERIFIED, CLOSED, CANCELLED, MARKED_DUPLICATE, SLA_WARNING, SLA_VIOLATED, ESCALATED. `record IncidentEvent(IncidentEventType type, Incident incident, User actor /*null = system*/, IncidentStatus previous /*null on creation*/, IncidentStatus current, String description, Instant at, List<Long> explicitRecipientIds /*empty = use default rules*/)`. `IncidentObserver` interface with `void onEvent(IncidentEvent)`. `IncidentEventPublisher` takes `List<IncidentObserver>` by constructor injection (Spring orders them by `@Order`) and calls each one in order, synchronously. An exception from an observer propagates, except that notification code must never throw (see 3.4).
  *Done when:* a test registers two fake observers and checks both are called in `@Order` order.

- [x] **3.2 Audit observer and history.** *Status: done.*
  **In plain words:** Every event is written to a permanent history log, so we can always see who did what and when. It cannot be edited afterwards.
  **Details for Claude:** `AuditObserver` (`@Order(1)`) writes one `AuditLog` row per event: incident, actor (null for system), `action` = event type name, previous and new status names, description. It runs inside the caller's transaction, so a failed audit write rolls back the business change. `AuditService.history(Long incidentId)` returns rows ordered by `createdAt`, then `id`.
  *Done when:* an integration test publishes an event and reads it back, and a second test shows a JDBC `UPDATE audit_logs ...` fails (the database trigger).

- [x] **3.3 Notification channels (Factory pattern).** *Status: done.*
  **In plain words:** The ways we can notify someone: a message inside the app and an email. A small factory hands out the right one, so a new channel (like SMS) can be added later.
  **Details for Claude:** `enum ChannelType { IN_APP, EMAIL }`. `record NotificationMessage(User recipient, Incident incident, IncidentEventType type, String text)`. `NotificationChannel` interface: `ChannelType type()`, `void send(NotificationMessage)`. `InAppChannel` saves a `Notification` row. `EmailChannel` uses `JavaMailSender`, sender from `iitsm.mail.from`, subject `[INC1001] <short event title>`, plain-text body, and catches and logs (warn) any mail exception instead of throwing. `NotificationChannelFactory.get(ChannelType)` builds its map from the injected `List<NotificationChannel>` and throws `IllegalArgumentException` for a missing type.
  *Done when:* tests cover the factory, the in-app save, and the email channel with a mocked `JavaMailSender` including the failure path.

- [ ] **3.4 Notification rules and observer.** *Status: not started.*
  **In plain words:** Decides who gets told about which event, and sends it in the background after the change is safely saved, so a mail problem can never break an incident update.
  **Details for Claude:** `NotificationService.notify(IncidentEvent)` applies this fixed table (recipients, then channels). If `explicitRecipientIds` is non-empty, those users replace the default recipients.

| Event | Recipients | Email too? |
|---|---|---|
| CREATED | reporter | yes |
| CLASSIFIED | none | n/a |
| PRIORITY_CHANGED | reporter, assignee | no |
| ASSIGNED | assignee, reporter | assignee only |
| ASSIGNMENT_FAILED | all users of type ADMINISTRATOR or IT_MANAGER | yes |
| ACKNOWLEDGED | reporter | no |
| INVESTIGATION_STARTED | none | n/a |
| INFO_REQUESTED | reporter | yes |
| INFO_RECEIVED | assignee | no |
| RESOLVED | reporter | yes |
| REOPENED | assignee | no |
| VERIFIED | assignee | no |
| CLOSED | reporter | no |
| CANCELLED, MARKED_DUPLICATE | reporter, assignee if any | no |
| SLA_WARNING | assignee, team lead of the assignee's team | no |
| SLA_VIOLATED | assignee, all administrators | yes |
| ESCALATED | explicit recipients only | yes |

  Every notification goes in-app; "Email too" adds the email channel. `NotificationObserver` (`@Order(2)`) must not run the sending inside the business transaction: if a transaction is active it registers an after-commit callback (`TransactionSynchronizationManager`) that calls an `@Async` method which re-loads the incident and users by id and saves notifications in a new transaction (`REQUIRES_NEW`); with no active transaction it dispatches directly. It catches and logs all exceptions.
  *Done when:* unit tests cover every row of the table; an integration test shows notifications appear only after commit, and a thrown exception in sending does not break the caller.

### Phase 4: Services (putting the pieces together)

- [ ] **4.1 Current user and access policy.** *Status: not started.*
  **In plain words:** Who is allowed to do what. An employee sees only their own incidents, a technician only theirs, admins everything.
  **Details for Claude:** `CurrentUserService.requireUser()` loads the `User` for the logged-in `UserPrincipal` id. `IncidentAccessPolicy` with `require...` methods throwing `UnauthorizedOperationException`, implementing exactly:

| Action | Allowed |
|---|---|
| view incident / its history | reporter; assignee; the team lead of the assignee's team; any admin |
| report incident | any authenticated user |
| acknowledge, start, request info, resolve, add NOTE | the assignee (NOTE also admin) |
| verify, reopen, respond to info, submit feedback | the reporter |
| cancel | the reporter or an admin |
| reassign, change priority, mark duplicate, close without feedback | admin |

  *Done when:* a unit test checks every cell of this table with one allowed and one denied user.

- [ ] **4.2 Incident factory (Factory pattern).** *Status: not started.*
  **In plain words:** One place that turns a submitted report form into a valid new incident, rejecting bad input.
  **Details for Claude:** `CreateIncidentCommand(String title, String description, Long categoryId, Long serviceId, short impact, short urgency, int affectedUsers)`. `IncidentFactory.create(command, reporter)`: loads the service (missing or inactive -> `ServiceNotFoundException`), loads the category (missing or inactive -> `InvalidInputException`), validates (title 1 to 200 characters, description non-blank, impact and urgency 1 to 3, affectedUsers at least 1), takes the number from `IncidentNumberGenerator`, and sets `createdAt = clock.instant()`. Returns an unsaved NEW incident. **The category is required at creation**; the suggest-category feature only helps the user choose it.
  *Done when:* tests cover the valid case and each validation failure.

- [ ] **4.3 Assignment service.** *Status: not started.*
  **In plain words:** Finds the right technician using whichever strategy the admin selected, skipping people who are unavailable or already full.
  **Details for Claude:** `AssignmentService`: `Optional<Technician> autoAssign(Incident)`, `List<Technician> recommend(Incident, int limit)`, `Technician assignManually(Incident, Long technicianId)`. Strategy is chosen by name from `ConfigService` key `assignment.strategy` (unknown name: log a warning and use `SKILL_BASED`). Eligible candidates are all technicians (team leads included) that are active and available and whose `countByAssigneeIdAndStatusIn(id, workload statuses) < maxWorkload`; `skillRequired` is true when the category has a required skill. After a successful auto-assignment call `strategy.recordAssigned`. `assignManually` throws `TechnicianUnavailableException` if the technician is inactive or unavailable and `WorkloadExceededException` if at capacity. Neither method changes the incident; the caller does that.
  *Done when:* integration tests with the seeded technicians cover each strategy, a full technician being skipped, no eligible technician returning empty, and both manual-assignment exceptions.

- [ ] **4.4 Duplicate detection service (intelligent feature 3).** *Status: not started.*
  **In plain words:** When someone reports a problem, check whether very similar open reports already exist for the same service and warn about them. Smart feature number three.
  **Details for Claude:** `DuplicateDetectionService.findCandidates(Incident)`: open incidents on the same service, created within `duplicate.window.hours` (default 24), excluding the incident itself, scored with `SimilarityStrategy` on `title + " " + description`, kept when the score is at least `duplicate.threshold` (default 0.3), top 5 by score. Returns `List<DuplicateCandidate(Long id, String number, String title, double score)>`. It never changes any incident.
  *Done when:* an integration test creates similar and dissimilar incidents on the same service and a similar one on another service, and only the right one is returned.

- [ ] **4.5 Create incident flow.** *Status: not started.*
  **In plain words:** The big one: reporting a problem triggers the whole chain (create, prioritize, set SLA deadlines, check duplicates, assign a technician, notify). This is demo workflow 1.
  **Details for Claude:** `IncidentService.create(command, actor)` in one transaction, returning `CreateIncidentResult(Incident incident, List<DuplicateCandidate> possibleDuplicates)`. Order: factory; save; publish CREATED (previous null, current NEW); priority via 2.1 (criticality from the service, bump threshold from `priority.bump.users`); policy via 1.7; deadlines via 2.3 starting at the incident's `createdAt`; `classify(...)`; save; publish CLASSIFIED; duplicate detection; `autoAssign` and, if a technician is found, `assignTo` and publish ASSIGNED, otherwise publish ASSIGNMENT_FAILED and leave the incident CLASSIFIED.
  *Done when:* integration test of demo workflow 1: a payment-outage incident from the seeded employee gets P1, SLA deadlines of 5 and 30 minutes from creation, an assignee chosen by the skill-based strategy, and audit rows for CREATED, CLASSIFIED and ASSIGNED.

- [ ] **4.6 Lifecycle operations.** *Status: not started.*
  **In plain words:** All the actions people take after creation: acknowledge, start, resolve, verify, reopen, cancel, close, mark duplicate, reassign, change priority. Each checks who is allowed, applies the change and announces the event.
  **Details for Claude:** Methods on `IncidentService`, each: load (missing -> `IncidentNotFoundException`), check `IncidentAccessPolicy`, call the 2.4 method, save, publish the matching event with previous and current status: `acknowledge`, `startProgress`, `resolve(id, text, actor)`, `verify`, `reopen(id, reason, actor)`, `cancel`, `close(id, actor)` (admin; VERIFIED -> CLOSED), `markDuplicate(id, originalId, actor)`, `reassign(id, technicianId, actor)` (only while CLASSIFIED or ASSIGNED, uses `assignManually`), `changePriority(id, Priority, actor)` (admin; recomputes deadlines with `pausedSeconds` and publishes PRIORITY_CHANGED). Reassignment after acknowledgement is out of scope.
  *Done when:* one integration test per operation, plus: an invalid transition returns the 409 mapping, a wrong actor is rejected, and the technician's active workload count drops when an incident becomes RESOLVED.

- [ ] **4.7 Comments and information requests.** *Status: not started.*
  **In plain words:** Notes, and the back-and-forth where a technician asks the user for more information. While waiting for the user, the SLA clock pauses.
  **Details for Claude:** `addNote(id, body, actor)`, `requestInfo(id, body, actor)` (assignee; saves an INFO_REQUEST comment, moves IN_PROGRESS -> PENDING_INFO via `requestInfo(now)`, publishes INFO_REQUESTED), `respondToInfo(id, body, actor)` (reporter; saves an INFO_RESPONSE comment; if the incident is PENDING_INFO it calls `receiveInfo(now)` and publishes INFO_RECEIVED; in any other open state it only saves the comment; on a closed incident it throws `InvalidStateTransitionException`), and `listComments(id, actor)`: the reporter sees INFO_REQUEST and INFO_RESPONSE only, staff see all types.
  *Done when:* an integration test covers workflow 2's middle section: the SLA clock pauses and the deadlines move later by the pause length after the reply.

- [ ] **4.8 Feedback and automatic close.** *Status: not started.*
  **In plain words:** After the user confirms the fix, they rate it, and the incident closes by itself.
  **Details for Claude:** `submitFeedback(id, rating 1 to 5, comments, actor)`: reporter only, incident must be VERIFIED, one feedback per incident (a second one throws `InvalidInputException`). After saving, the system closes the incident (`close(now)`, actor null) and publishes CLOSED.
  *Done when:* tests cover the happy path, wrong status, second submission, rating 0 and 6.

- [ ] **4.9 SLA monitor.** *Status: not started.*
  **In plain words:** A background job that keeps checking deadlines and raises a warning at 80% and a violation when the deadline passes, once each.
  **Details for Claude:** `SlaMonitorService.runOnce()` (public, uses `Clock`): for every open incident that has a priority, evaluate with 2.3; if the result is more severe than the stored `slaStatus`, call `raiseSlaStatus`, save, and publish SLA_WARNING (AT_RISK) or SLA_VIOLATED (VIOLATED) **once per change**. Each incident is processed in its own try/catch so one failure does not stop the run. A separate `SlaMonitorScheduler` class calls `runOnce()` with `@Scheduled(fixedDelayString = "${iitsm.sla.monitor-interval-ms}")` (a separate class avoids Spring's self-invocation problem with transactions).
  *Done when:* an integration test using `MutableClock` shows ON_TRACK, then AT_RISK after advancing to 80%, then VIOLATED after the deadline, with exactly one event each and none on repeat runs.

- [ ] **4.10 Auto-escalation (intelligent feature 4).** *Status: not started.*
  **In plain words:** If a critical (P1) incident is not acknowledged in time, the system alerts the next person up the chain, then the next. Smart feature number four.
  **Details for Claude:** `EscalationService.runOnce()`: candidates are P1 incidents in status ASSIGNED (not acknowledged) with `escalationLevel < 3`. With `n = escalationLevel + 1`, escalate when `now >= responseDeadline + (n - 1) x levelTimeoutMinutes x 60 / timeScale` seconds. Targets: level 1 = the team lead of the assignee's team (if the team has none, all exact ADMINISTRATOR users); level 2 = all exact ADMINISTRATOR users; level 3 = all IT_MANAGER users. It calls `incrementEscalationLevel()`, saves, and publishes ESCALATED with `explicitRecipientIds` set to the targets and a description naming the level. It does **not** reassign the incident. At most one level per incident per run. An `EscalationScheduler` calls it on the same interval as 4.9.
  *Done when:* an integration test with `MutableClock` shows level 1 at the response deadline, level 2 one timeout later, level 3 one more later, and nothing after acknowledgement.

### Phase 5: REST API (what the frontend will call)

- [ ] **5.1 DTOs and mappers.** *Status: not started.*
  **In plain words:** Clean, simple data shapes for what the API sends and receives, so internal database objects never leak out.
  **Details for Claude:** Records in `dto/`: `IncidentSummaryResponse` and `IncidentDetailResponse` (ids and names for reporter, assignee, service, category; priority, status, slaStatus, deadlines, resolution, `duplicateOfNumber`), `CreateIncidentRequest` (Jakarta validation annotations mirroring 4.2), `CreateIncidentResponse` (detail plus `possibleDuplicates`), `CommentResponse`, `AuditResponse`, `NotificationResponse`, `FeedbackResponse`, `PageResponse<T>(content, page, size, totalElements, totalPages)`. A mapper component converts entities to DTOs. Timestamps serialize as ISO-8601 strings.
  *Done when:* mapper unit tests pass and no controller signature mentions an entity.

- [ ] **5.2 Incident endpoints.** *Status: not started.*
  **In plain words:** The web addresses for reporting and working on incidents.
  **Details for Claude:** `IncidentController` under `/api/incidents`: `POST /` (201, any authenticated user); `GET /{id}`; `POST /suggest-category` (body title and description, returns top 3 with scores); `POST /{id}/acknowledge`, `/start`, `/resolve` (body `resolution`), `/verify`, `/reopen` (body `reason`), `/cancel`; admin only (`@PreAuthorize("hasRole('ADMIN')")`): `POST /{id}/close`, `/duplicate` (body `originalId`), `/reassign` (body `technicianId`), `/priority` (body `priority`), `GET /{id}/recommendations` (top 3 technicians); `GET /{id}/comments` and `POST /{id}/comments` (body `type`, `body`, routed to 4.7 by type); `POST /{id}/feedback`, `GET /{id}/feedback`; `GET /{id}/history`. Ownership is enforced by the service layer, role by annotations.
  *Done when:* MockMvc tests with real JWTs for employee, technician and admin cover 200/201, 400 (validation), 401 (no token), 403 (wrong role or not the owner), 404 and 409 for representative endpoints.

- [ ] **5.3 Search and filtering.** *Status: not started.*
  **In plain words:** Finding incidents by number, status, priority, category, service, technician, SLA state or date, with pages. Each person only sees what they are allowed to.
  **Details for Claude:** `GET /api/incidents` with optional params `incidentNumber` (case-insensitive contains), `status`, `priority`, `categoryId`, `serviceId`, `technicianId`, `slaStatus`, `createdFrom`, `createdTo` (ISO dates, inclusive), plus `page` (default 0), `size` (default 20, max 100), `sort` (default `createdAt,desc`). Built with `IncidentSpecifications` (JPA Specification builders combined with `and`). Results are scoped by caller: employee sees only incidents they reported; technician sees only incidents assigned to them, and a team lead also those assigned to technicians in their team; admin sees all. `GET /api/technicians/me/workload` returns `{active, max}`.
  *Done when:* integration tests cover each filter alone, several combined, pagination, and scoping per role.

- [ ] **5.4 Notification and audit endpoints.** *Status: not started.*
  **In plain words:** Let users read their own notifications, and let admins browse the audit history.
  **Details for Claude:** `GET /api/notifications` (own, optional `unreadOnly`, paged), `GET /api/notifications/unread-count`, `POST /api/notifications/{id}/read` (own only, else 403), `POST /api/notifications/read-all`. `GET /api/audit` (admin only) with filters `incidentId`, `userId`, `from`, `to`, paged.
  *Done when:* tests cover scoping (a user cannot read or mark another user's notification) and the admin-only rule.

- [ ] **5.5 Registration, current user and user administration.** *Status: not started.*
  **In plain words:** Employees can sign up themselves; admins create and manage users, teams, technicians and their skills.
  **Details for Claude:** `POST /api/auth/register` (public, add to the permit list in `SecurityConfig`; always creates an EMPLOYEE; email unique -> `DuplicateResourceException`; password at least 8 characters). `GET /api/auth/me`. `UserFactory.create(CreateUserCommand)` (Factory pattern) builds the right subclass for a `Role` and BCrypt-hashes the password. Admin endpoints under `/api/admin`: `POST /users`, `GET /users` (filter by role, paged), `GET /users/{id}`, `PUT /users/{id}` (fullName, active, department), `PUT /users/{id}/password`; `GET/POST /teams`, `PUT /teams/{id}/lead`; `PUT /technicians/{id}` (maxWorkload, available, experienceYears, teamId); `PUT /technicians/{id}/skills` (replaces the full set of `{skillId, proficiency}`). `GET /api/skills` for any authenticated user. `PUT /api/technicians/me/availability` lets a technician toggle themselves.
  *Done when:* tests cover registration, duplicate email, every role created through the factory, the skill replacement, and non-admin rejection.

- [ ] **5.6 Configuration administration.** *Status: not started.*
  **In plain words:** Admin screens' backend: manage IT services, categories, SLA times and which assignment strategy is active.
  **Details for Claude:** Admin CRUD under `/api/admin`: `services`, `categories` (deactivate by `active=false`, never delete), `sla-policies` (a duplicate priority and service pair -> 409). `GET/PUT /api/admin/config/assignment-strategy` (body `strategy`, must be one of the three names, else 400). Read-only `GET /api/services` and `GET /api/categories` (active only) for any authenticated user, used by the report form.
  *Done when:* tests cover each endpoint's happy path, validation, and that switching the strategy changes the next auto-assignment.

- [ ] **5.7 Reports (admin only).** *Status: not started.*
  **In plain words:** The numbers for the dashboard: totals, who is busy, which services break most, SLA health, incidents per day.
  **Details for Claude:** Under `/api/reports`: `overview` (`total`, `open`, `critical` = open P1, `resolved` = status RESOLVED, VERIFIED or CLOSED, `slaViolations` = slaStatus VIOLATED, `averageRating`); `technician-workload` (technician, active count, max); `service-analysis` (service, count); `category-distribution`; `priority-distribution`; `sla-compliance` (counts of each SlaStatus); `incidents-per-day?days=14` (zero-filled). Implemented as grouped JPQL queries.
  *Done when:* integration tests with known data assert exact numbers for each report.

### Phase 6: Demo and test suite

- [ ] **6.1 Demo profile and script.** *Status: not started.*
  **In plain words:** A speed-up setting so deadlines and escalations happen in seconds during the recorded demo, plus a written script of the three workflows to follow.
  **Details for Claude:** `application-demo.yml` with `iitsm.sla.time-scale: 20`, `iitsm.escalation.level-timeout-minutes: 1`, `iitsm.sla.monitor-interval-ms: 5000`; started with `SPRING_PROFILES_ACTIVE=dev,demo`. `docs/demo-script.md` describes the three workflows step by step with the seeded users: (1) employee reports a payment outage, gets P1, assigned, notified by in-app and email (Mailpit); (2) technician acknowledges, requests info (SLA pauses), employee replies, technician resolves, employee verifies and gives feedback, incident closes; (3) several similar payment incidents flag duplicates, and an unacknowledged P1 escalates through the chain.
  *Done when:* the script has been followed once against the running backend (API calls from Swagger or a REST client) and every step behaved as written.

- [ ] **6.2 Workflow integration tests.** *Status: not started.*
  **In plain words:** Automated tests that replay the three demo workflows, moving a fake clock forward instead of waiting.
  **Details for Claude:** Three `@SpringBootTest` classes, one per demo workflow, extending `AbstractIntegrationTest`, using `MutableClock` and calling `runOnce()` on the monitor and escalation services instead of waiting.
  *Done when:* all three pass in `mvn verify`.

- [ ] **6.3 Test case catalogue.** *Status: not started.*
  **In plain words:** The written list of at least 18 test cases that the course requires, linked to the real automated tests.
  **Details for Claude:** `docs/TEST_CASES.md`: at least 18 cases, each with ID, category (normal, boundary, invalid input, exception, authorization, business rule), steps, expected result and the name of the automated test that covers it. Must include: priority thresholds, workload at max, SLA at exactly 80%, blank description, `NEW -> CLOSED`, missing SLA configuration, employee viewing another's incident, technician calling an admin endpoint, SLA pause, escalation chain, duplicate detection.
  *Done when:* every listed test name exists in the code.

### Phase 7: Frontend (React, in `frontend/`)

Fixed choices: **Vite + React 18, JavaScript (not TypeScript)**, `react-router-dom` v6, `axios`, `recharts`, plain CSS (no UI framework). API base URL from `VITE_API_URL` (default `http://localhost:8080`). The token is stored in `localStorage` under `iitsm_token`. Role groups: employee = EMPLOYEE; technician = TECHNICIAN or TEAM_LEAD; admin = ADMINISTRATOR or IT_MANAGER. Impact and urgency selects show Low, Medium, High (sent as 1, 2, 3).

- [ ] **7.1 Scaffold, API client and auth.** *Status: not started.*
  **In plain words:** Create the React app, and make login, registration and role-based redirects work.
  **Details for Claude:** Vite project; axios instance that attaches the token and, on a 401, clears it and goes to `/login`; `AuthContext` (login, register, logout, current user from `/api/auth/me`); Login and Register pages; `ProtectedRoute` with a required-role-group prop.
  *Done when:* each demo user can log in and is redirected to its role's home route.

- [ ] **7.2 App shell and notifications.** *Status: not started.*
  **In plain words:** The page frame with a menu per role and a notification bell.
  **Details for Claude:** Layout with navigation per role group, a notification bell polling `/api/notifications/unread-count` every 15 seconds, a dropdown listing notifications with mark-read and mark-all-read.
  *Done when:* an incident event produces a visible unread badge within one poll.

- [ ] **7.3 Employee pages.** *Status: not started.*
  **In plain words:** Everything an employee does: report a problem, see their incidents, answer questions, confirm the fix, rate it.
  **Details for Claude:** Report-incident form (service and category dropdowns from the read-only endpoints; a "suggest category" action calling `/suggest-category`; impact, urgency, affected users); after submit a panel showing `possibleDuplicates`. My-incidents list with the status filter. Incident detail: details, info-request thread with a reply box, history timeline, Verify and Reopen buttons when RESOLVED, a feedback form when VERIFIED, Cancel when allowed.
  *Done when:* workflow 1 and the employee half of workflow 2 can be done entirely in the UI.

- [ ] **7.4 Technician pages.** *Status: not started.*
  **In plain words:** Everything a technician does: see assigned incidents and workload, investigate, ask for info, resolve.
  **Details for Claude:** Assigned-incidents list, workload card (`active / max`), availability toggle, incident detail with Acknowledge, Start, Request info, Add note and Resolve actions shown only in the statuses where they are valid.
  *Done when:* the technician half of workflow 2 can be done in the UI.

- [ ] **7.5 Admin incident pages.** *Status: not started.*
  **In plain words:** Admins search all incidents with every filter, step in (reassign, change priority, mark duplicate, close) and browse the audit log.
  **Details for Claude:** Incident search page with every filter from 5.3 and pagination; admin detail view adding Reassign (showing the recommendations list), Change priority, Mark duplicate and Close; audit log page with the 5.4 filters.
  *Done when:* every filter changes the results and every admin action works.

- [ ] **7.6 Admin configuration pages.** *Status: not started.*
  **In plain words:** Screens to manage users, teams, technicians' skills, services, categories, SLA times and the assignment strategy.
  **Details for Claude:** Users (create, edit, deactivate), teams and leads, technicians (workload, availability, skills with proficiency), services, categories (with keywords), SLA policies, and the assignment-strategy switch.
  *Done when:* creating a category with keywords makes it appear in the report form and the suggestion.

- [ ] **7.7 Dashboard.** *Status: not started.*
  **In plain words:** Charts and numbers for managers.
  **Details for Claude:** Overview cards plus Recharts charts for technician workload, service analysis, priority distribution, SLA compliance and incidents per day.
  *Done when:* numbers match the report endpoints.

### Phase 8: Innovation and submission documents

- [ ] **8.1 Real-time updates with Server-Sent Events (innovation, optional).** *Status: not started.*
  **In plain words:** Updates appear on screen instantly without refreshing. Extra credit; do it only after everything above works.
  **Details for Claude:** `GET /api/stream` returning an `SseEmitter`. Browsers cannot set headers on `EventSource`, so this one endpoint also accepts the JWT as a `?token=` query parameter (validated by the existing `JwtService`). A new observer (`@Order(3)`) pushes `notification` events to the affected users and `incident-changed` events to connected admins. The frontend bell and dashboard refresh on these events and keep polling as a fallback.
  *Done when:* a status change appears in a second browser session without a reload.

- [ ] **8.2 Diagrams.** *Status: not started.*
  **In plain words:** The architecture, use case, class and database diagrams the course requires.
  **Details for Claude:** In `docs/`: architecture diagram (frontend, REST API, controller, service, repository, PostgreSQL, SMTP/Mailpit, schedulers), use case diagram (actors: employee, technician, team lead, administrator, IT manager, and the system scheduler), class diagram (user hierarchy, incident with its state classes, strategy interfaces and implementations, observer interface and implementations, notification channels, factories), and an ER diagram generated from `V1__baseline_schema.sql`.
  *Done when:* diagrams are committed as image or Mermaid files and match the code.

- [ ] **8.3 Project report.** *Status: not started.*
  **In plain words:** The written report covering problem, design, technology choices, database, testing and security.
  **Details for Claude:** Sections required by the spec: problem analysis, requirements, architecture, UML, technology stack, database design with sample data, testing, security considerations (list what is not covered: refresh tokens, rate limiting, account lockout, `localStorage` token storage). The Technology Selection Report answers all nine questions from the spec, including how the system would scale (stateless JWT, read replicas, a message queue for notifications, a scheduler lock).
  *Done when:* every section of the spec's Deliverables list has a matching section.

- [ ] **8.4 Demo video.** *Status: not started.*
  **In plain words:** The recorded demo you submit.
  **Details for Claude:** 15 to 20 minutes, three workflows from `docs/demo-script.md`, every member introduces themselves and explains a module; file named per the spec; upload before 22 Nov 2026.
  *Done when:* uploaded and the link confirmed.

---

### Decisions this checklist makes that differ from or add to the earlier plan

1. **DUPLICATE is allowed from NEW, CLASSIFIED, ASSIGNED and ACKNOWLEDGED** (the earlier plan said NEW or CLASSIFIED only). Incidents are auto-assigned on creation, so the earlier rule would never have let an admin mark a duplicate.
2. **Category is required when reporting.** The suggestion is a helper, not an automatic fill.
3. **Escalation notifies and records; it does not reassign the incident.** Level 1 is the assignee's team lead, level 2 the administrators, level 3 the IT managers.
4. **Skill-based assignment has no fallback.** If nobody has the skill, the incident stays CLASSIFIED and admins are notified (ASSIGNMENT_FAILED).
5. **Team leads are assignable like technicians.** Reassignment after acknowledgement is out of scope.
6. **SLA clock starts at the incident's creation time** and pauses only in PENDING_INFO.
7. **Duplicate similarity threshold lowered from 0.5 to 0.3** (migration V4). Short titles rarely reach 0.5 even for the same problem. Tune it in `system_config` if the demo data needs it.
8. **Frontend is JavaScript, not TypeScript**, to reduce setup cost for a first project.


---

# 3. Scaffold guide (what is in the repo)

A plain-language tour of the starter code: what each part is, why it exists, and how the pieces fit together. A glossary is at the end.

## 3.1 The big picture

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

## 3.2 Folder structure

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

## 3.3 The layered architecture (the most important idea)

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

## 3.4 The database

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

## 3.5 The user hierarchy (inheritance, explained)

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

## 3.6 Security, step by step

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

## 3.7 Configuration and profiles

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

## 3.8 Docker Compose

- **postgres:** the database on port 5432 with a named volume so data survives restarts. To wipe everything: `docker compose down -v`.
- **mailpit:** accepts emails on port 1025 and shows them at http://localhost:8025. No real emails are sent.

The backend itself is not in Compose yet; run it from your IDE or `mvn spring-boot:run`.

## 3.9 Error handling

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

## 3.10 The test

`IitsmApplicationTests` is a **smoke test**. It uses **Testcontainers** to start a throwaway Postgres in Docker, then:

1. starts the full application,
2. runs all Flyway migrations,
3. lets Hibernate validate that every mapped class matches its table,
4. loads the demo users and confirms each is the correct subclass (and a team lead is also a `Technician`).

If it passes, migrations, mappings and inheritance agree. Docker must be running.

## 3.11 Design patterns visible so far

| Pattern | Where | Status |
|---|---|---|
| Adapter | `UserPrincipal` adapts `User` to Spring Security's `UserDetails` | Present |
| Repository | Spring Data repositories such as `UserRepository` | Present (framework-provided) |
| State | Incident lifecycle | Planned |
| Strategy | Assignment, priority calculation, similarity | Planned |
| Observer | Audit logging and notifications on incident changes | Planned |
| Factory | Notification channel creation | Planned |

## 3.12 Working in the repo: conventions

- **Put code in the right layer.** Controller: HTTP only. Service: rules. Repository: database.
- **Pick the entity base class from the table's columns** (Hibernate runs in `validate` mode and fails startup on any mismatch): `BaseEntity` for tables with `created_at` and `updated_at` (`users`, `teams`, `incidents`); `CreatedOnlyEntity` for tables with only `created_at` (`incident_comments`, `audit_logs`, `notifications`, `feedback`); no base class (own `@Id`) for tables with no timestamps (`skills`, `categories`, `it_services`, `sla_policies`, `system_config`, `technician_skills`).
- **Map SQL types exactly:** `SMALLINT` -> `short`, `INT` -> `int`, `BIGINT` -> `Long`, `TIMESTAMPTZ` -> `Instant`, enum columns -> `@Enumerated(EnumType.STRING)`. Mapping a `SMALLINT` column to `int` fails validation.
- **Never return entities from controllers.** Use DTOs so API shapes do not leak database details.
- **Schema changes only through new Flyway files.**
- **No secrets in Git.**
- **Throw our custom exceptions** for business errors.
- **Run `mvn verify` before pushing.**
- **Commit small and often** with clear messages, and pull before you push.

## 3.13 What does not exist yet

- Incident entity, creation, priority calculation
- The lifecycle state machine
- Technician assignment strategies
- SLA monitoring (scheduling is enabled but nothing is scheduled yet)
- Audit and notification observers; email sending
- Search, filtering, reports, dashboards
- Duplicate detection, auto-categorization, auto-escalation
- The React frontend
- Java entities for skills, categories, services, SLA policies, comments, audit logs, notifications and feedback. **The tables exist, the classes do not yet.** Hibernate only validates tables that have a matching class, so this is harmless.

## 3.14 Glossary

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
