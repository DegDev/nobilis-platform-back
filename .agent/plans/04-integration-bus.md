# Plan: Integration bus + notification dispatcher (milestone 04)

## Feature ID
04-integration-bus

## Scope
backend only. Front is not involved this slice — no consumers/producers exist yet in `app`/`admin`
that would need a frontend counterpart; the worker (`integration`) is a headless process with no UI.

## Applicable playbook
- No `[ready]` playbook matches this task class exactly. Two `[anticipated]` entries in
  `docs/playbooks/README.md` name it directly: `async-consumer.md` ("a Kafka consumer in the
  integration worker: topic → handler → DLQ topic on failure... milestone 04") and
  `notification-dispatch.md` ("event → template (locale) → transport... milestone 04"). Neither is
  written yet — per this repo's "extract, don't predict" principle, they get extracted from THIS
  slice once it lands, not guessed in advance. This plan is the real instance both playbooks will be
  drawn from; do not pre-write them now.
- `lombok-conventions.md` **[ready]** applies as usual to any new entity/record/service in this slice.
- fullstack: none — backend-only slice, no paired front plan.

## Goal
A worker process consumes domain-agnostic events off a broker-neutral bus port and, for the first
vertical slice, dispatches ONE kind of consumer: a notification dispatcher that resolves a template
(type + transport + locale, with `ru` fallback) and delivers it over ONE transport (email via
Mailpit). Slice 1 (the bus port + Kafka adapter + worker boot) is closed and merged; this plan
formalizes it post-hoc and specifies slice 2 (the dispatcher) as the actionable next build pass.

## Architectural decisions

### Bus architecture (locked 2026-07-11)
The broker sits behind a neutral port, `EventBus`/`EventHandler`
(`common/src/main/java/io/github/degdev/engine/common/bus/EventBus.java`,
`.../bus/EventHandler.java`). **Model A: exactly one active broker**, selected by opt-in
autoconfig (`nobilis.integration.bus=kafka`) — not a runtime multi-broker abstraction. Consumers
depend on the port, never on Kafka directly. RabbitMQ is a **future alternative adapter** behind the
same port (infra already in `stack.yml` — `rabbitmq:4-management`, AMQP 5672 / UI 15672 — but zero
adapter code); this is extract-don't-predict, not a rejection (see `docs/sources-log.md`, the
"reconciles the earlier 'Kafka instead of RabbitMQ' row" entry).

### Slice 1 — CLOSED, merged to `main` (recorded here for the milestone's provenance)
- `EventBus` + `EventHandler` port in `common/.../bus/` (broker-neutral).
- Kafka adapter `common/.../bus/kafka/` (`KafkaEventBus`, `KafkaEventBusProperties`,
  `KafkaEventBusAutoConfiguration`) — opt-in via `@ConditionalOnProperty`, own producer/consumer
  factories (not Spring Boot's own `KafkaAutoConfiguration`), registered in
  `AutoConfiguration.imports`, mirrors `CryptoAutoConfiguration`'s shape.
- Worker (`integration` module) consumes a trivial ping via the port
  (`integration/.../ping/PingEventHandler.java`) with a test-only producer
  (`integration/.../ping/PingDemoRunner.java`).
- Fixed the worker's missing datasource exclusions — `common` puts JPA/Hibernate/Flyway on every
  dependent's classpath; `integration/src/main/resources/application.properties` now carries the
  same `spring.autoconfigure.exclude` as `app`/`admin`. **This exclusion is what slice 2 partially
  reverses** (see decision 1 below) — the worker becomes a legitimate DB consumer, not stateless.
- `stack.yml` gained RabbitMQ infra (unused by code this slice).
- Proven live: Testcontainers Kafka round-trip test, and a revert-then-reapply to confirm the test
  actually catches breakage.

### Slice 2 — notification dispatcher (THIS BUILD PASS)
Goal: an event arrives on the bus → dispatcher resolves the matching template
(`typeKey` + `transport` + `locale`, `ru` fallback) → delivers via ONE transport (email/Mailpit).

**Locked decisions (2026-07-11, recon-confirmed — do not re-litigate):**

1. **DB access in the worker (the slice-1→2 blocker).** `NotificationsService` is
   `@ConditionalOnBean(EntityManagerFactory.class)`
   (`common/.../notifications/NotificationsAutoConfiguration.java`) — the worker currently excludes
   JPA/DataSource/Flyway, so it cannot get that bean. Resolved: **(A) re-enable JPA in
   `integration`** — remove the `spring.autoconfigure.exclude` line added in slice 1, since the
   worker is now a legitimate notifications-data consumer (an evolution of its role, not a band-aid
   reintroducing the slice-1 bug). Rejected alternatives: **(B) HTTP call to `admin`'s notification
   endpoints** — breaks the runnable graph (worker would depend on another runnable being up,
   introduces a synchronous coupling a message-bus architecture exists specifically to avoid).
   **(C) a narrower repository-only JPA slice** — `NotificationsService` itself is gated on the
   `EntityManagerFactory` bean, so partial JPA wiring is a half-measure that still needs the full
   auto-configuration chain to produce a working `EntityManagerFactory`; no actual scope reduction.
2. **Transport PORT now**, not deferred. Mirrors the `EventBus` port precedent structurally (a plain
   interface + one concrete adapter + opt-in autoconfig). Justified here (unlike a bare "port for one
   caller" abstraction) because Telegram and SMS are **named, locked future consumers of this same
   port** in the milestone's own scope (`Transport` enum already has `EMAIL, TELEGRAM, SMS` —
   `common/.../notifications/Transport.java`) — not speculative. Email is the first and only adapter
   implemented this slice; the dispatcher calls the port, never a concrete mail sender directly.
3. **Event contract = a typed record + Jackson**, living in `common` (bus package), NOT the bare
   `String` payload slice 1 used for the ping. Fields: `{typeKey, locale, transport?, recipient,
   vars?}` (`vars` reserved for later interpolation, unused this slice — see decision 6). Jackson
   becomes an explicit dependency of `common` if not already transitively resolvable there; confirm
   during GATE-0, add explicitly if needed (do not rely on an accidental transitive).
4. **Code location:**
   - Dispatcher (`EventHandler` implementation) + the transport port + its email adapter → in
     `integration` (mirrors where `PingEventHandler` already lives, package `notifications/` or
     `dispatch/` — name at implementation time, not predicted here).
   - Template-resolution buildout → in `common/.../notifications/`, extending
     `NotificationsService`. `findTemplate(typeKey, transport)` currently returns the whole template
     with all translations loaded but does **no** locale selection, **no** `ru` fallback, and **no**
     `NotificationType.enabled` filtering. Mirror the CMS precedent
     (`common/.../cms/ContentBlockService.java`: `readPublished` + `resolveBody`, exact-locale then
     `ru`-fallback via `LocaleResolver`) — add an equivalent resolve method here rather than doing
     ad-hoc translation-picking inside the dispatcher.
   - Event contract (the typed record) → in `common/.../bus/` (co-located with `EventBus`/
     `EventHandler`, since it's a bus-level concern, not notifications-specific — a future non-
     notification event could reuse the envelope shape, though only notifications instantiates it
     this slice).
5. **Transport = email via Mailpit**, first and only adapter this slice. Spring Boot 4.1's
   `spring-boot-starter-mail` (module `spring-boot-mail`,
   `org.springframework.boot.mail.autoconfigure.MailProperties`), autoconfigures a `JavaMailSender`
   off `spring.mail.host` — point it at the already-provisioned Mailpit SMTP (`stack.yml`, port
   1025). No other transport infra exists yet (Telegram/SMS have neither code nor stack.yml
   entries) — confirmed settled by infra availability, not a preference call.
6. **Interpolation deferred.** No `{{ }}` or any placeholder mechanism exists anywhere in the
   codebase (confirmed empty search across `common`) — this slice sends the template's static
   subject/body text as-is; the `vars` field on the event contract (decision 3) is reserved but
   unused. **Failure handling this slice: drop + log at WARN** (no template match, disabled type, or
   SMTP failure — none of these retry or DLQ this slice). **Retry + DLQ deferred to slice 3**, and
   explicitly at the **bus level** (Kafka redelivery → dead-letter topic), not a transport-level
   retry loop in the dispatcher — matches the `async-consumer.md` playbook's anticipated shape
   ("topic → handler → DLQ topic on failure").
7. **Testing the email path.** No Mailpit-Testcontainers precedent exists in this repo. Options at
   build time: a `GenericContainer` wrapping `axllent/mailpit` (new pattern, mirrors the
   `KafkaEventBusIntegrationTest`'s `@Testcontainers` shape), or querying the already-running
   `stack.yml` Mailpit instance over its HTTP API (`GET /api/v1/messages`, port 8025) — less
   hermetic, no isolation between test runs. Pick at GATE-0/implementation time; not locked here.

### Slice 2 — build details (naming proposals, verify at GATE-0)

Concrete evidence pulled from the actual current signatures (not guessed) so the build pass has a
starting shape instead of blank naming. Still confirm at GATE-0 before writing — these are proposals
that fit the existing precedent exactly, not locked contracts.

**Template resolve method — mirrors `ContentBlockService.readPublished`/`resolveBody` exactly.**
Current `NotificationsService.findTemplate(String typeKey, Transport transport)`
(`common/.../notifications/NotificationsService.java`) returns `Optional<NotificationTemplate>` with
all translations loaded, no locale/enabled filtering. `ContentBlockService.readPublished(key,
locale)` → `resolveBody(block, locale)` is the exact pattern to mirror: resolve the locale tag via
`localeResolver.resolve(locale).toLanguageTag()`, look for a translation matching that tag, else fall
back to `LocaleResolver.DEFAULT_LOCALE.toLanguageTag()` (`"ru"`), stream-filter
`NotificationTemplateTranslation` by `.getLocale().equals(...)`. Proposed addition to
`NotificationsService`:

```java
@Transactional(readOnly = true)
public Optional<NotificationTemplateTranslation> resolveForDispatch(
    String typeKey, Transport transport, String locale) {
  NotificationType type = requireType(typeKey);
  if (!type.isEnabled()) {
    return Optional.empty();
  }
  return templateRepository
      .findByTypeIdAndTransport(type.getId(), transport)
      .flatMap(template -> resolveTranslation(template, locale));
}

private Optional<NotificationTemplateTranslation> resolveTranslation(
    NotificationTemplate template, String locale) {
  String resolvedTag = localeResolver.resolve(locale).toLanguageTag();
  return translationFor(template, resolvedTag)
      .or(() -> translationFor(template, LocaleResolver.DEFAULT_LOCALE.toLanguageTag()));
}
```
(`translationFor` — same stream-filter helper as `ContentBlockService`'s, over
`template.getTranslations()`.) Confirm `NotificationType.isEnabled()`'s actual accessor name at
GATE-0 (Lombok `@Getter` — likely `isEnabled()` for a `boolean` field, verify against
`NotificationType.java` rather than assuming).

**Event contract — proposed `NotificationEvent` record**, `common/.../bus/`:
```java
public record NotificationEvent(
    String typeKey, String locale, Transport transport, String recipient, Map<String, String> vars) {}
```
`Transport` already lives in `common/.../notifications/Transport.java` — the bus package depends on
notifications for this one type, or the field becomes a `String` transport code if that dependency
direction is unwanted (bus is lower-level than notifications conceptually). **Flag at GATE-0**: check
whether `common/.../bus/` importing from `common/.../notifications/` is acceptable package-by-feature
layering, or whether `NotificationEvent` belongs inside `notifications/` instead, publishing on the
bus via a serialized form. Not locked — genuine open question, listed below too.

**Dispatcher — proposed `integration/.../dispatch/NotificationDispatchEventHandler.java`**,
implementing `EventHandler` (`topic()` returns the notification-dispatch topic name, `handle(String
payload)` deserializes to `NotificationEvent` via Jackson, calls
`notificationsService.resolveForDispatch(...)`, then the transport port). Package name `dispatch/`
chosen over `notifications/` to avoid colliding with the `common` package of the same name when both
are imported in the same file — a naming nit, confirm no strong preference exists at GATE-0.

**Transport port — proposed `integration/.../dispatch/NotificationTransport.java`** (interface,
mirrors `EventBus`'s one-method shape):
```java
public interface NotificationTransport {
  void send(String recipient, String subject, String body);
}
```
Adapter: `EmailNotificationTransport implements NotificationTransport`, `@Service`, wraps
`JavaMailSender` (`spring-boot-starter-mail`). Single adapter this slice — `@Primary`/qualifier only
becomes relevant when a second transport (Telegram) is added in slice 3+; until then a plain
`@Service` singleton is sufficient (no port-selection autoconfig needed yet, unlike `EventBus` which
had two candidate adapters from day one — Kafka now, RabbitMQ infra already provisioned).

### Slice 3+ (name only — do not design, do not build)
- Retry + DLQ at the bus level — now designed, see `## Slice 3 — bus-level retry + DLQ` below.
- Telegram transport (second adapter behind the transport port from decision 2).
- SMS transport (stub — no real provider integration implied by the name alone).
- Template variable interpolation (activates the `vars` field reserved in decision 3).
- Scheduler harness (unrelated consumer class, not part of the dispatcher).

## Files to create / change

### Backend

Modules touched: `common`, `integration`.

- `integration/src/main/resources/application.properties` — remove the
  `spring.autoconfigure.exclude` line (DataSource/Hibernate/DataJpaRepositories/Flyway) added in
  slice 1; the worker now boots with a datasource, mirroring `app`/`admin`.
- `common/src/main/java/io/github/degdev/engine/common/bus/` — new typed event record (name TBD at
  build time, e.g. `NotificationEvent`) with `{typeKey, locale, transport?, recipient, vars?}`.
- `common/pom.xml` — confirm/add Jackson as an explicit dependency if not already resolvable
  (GATE-0 check, don't assume).
- `common/src/main/java/io/github/degdev/engine/common/notifications/NotificationsService.java` —
  add a resolve method mirroring `ContentBlockService.readPublished`/`resolveBody`: locale-select +
  `ru`-fallback + `NotificationType.enabled` filtering on top of the existing `findTemplate`.
- `integration/src/main/java/io/github/degdev/engine/integration/` — new package (name TBD, e.g.
  `notifications/` or `dispatch/`) containing:
  - the dispatcher `EventHandler` implementation (mirrors `PingEventHandler`'s registration shape),
  - the transport port interface (mirrors `EventBus`/`EventHandler`'s shape: interface + one opt-in
    adapter),
  - the email adapter (`spring-boot-starter-mail`, `JavaMailSender` against `spring.mail.host`
    pointed at Mailpit).
- `integration/pom.xml` — add `spring-boot-starter-mail`.
- `integration/src/main/resources/application.properties` — add `spring.mail.host`/`port` for
  Mailpit (dev profile).

### Frontend
None this slice.

## Open questions
1. **Package/class naming** — narrowed by the "Slice 2 — build details" section above to concrete
   proposals (`NotificationDispatchEventHandler`, `NotificationTransport`,
   `EmailNotificationTransport`, all in `integration/.../dispatch/`). Confirm at GATE-0 against what
   recon-inside finds — these fit the existing `ping/` package shape but are proposals, not locked.
2. **`NotificationEvent`'s package** — `common/.../bus/` (bus-level, broker-neutral envelope) vs.
   `common/.../notifications/` (avoids `bus/` depending on `notifications/`'s `Transport` enum).
   Genuinely undecided — pick at GATE-0 based on which reads more natural once the dispatcher code
   exists to reference it both ways.
3. Whether `common` already transitively carries Jackson on its classpath (web/JPA often pull it in)
   or needs an explicit dependency — a GATE-0 check, not a plan-time guess.
4. Mailpit test approach — `GenericContainer` vs. live `stack.yml` instance (decision 7) — pick at
   implementation time based on what's actually easiest to wire cleanly.
5. Exact accessor name for `NotificationType`'s enabled flag (`isEnabled()` assumed from Lombok
   `@Getter` convention on a `boolean` field — confirm against `NotificationType.java` at GATE-0
   rather than compiling against a guess).

## Testing strategy

### Backend
- Unit tests: the new `NotificationsService` resolve method (locale exact-match, `ru`-fallback,
  disabled-type exclusion, no-match case) — mirrors existing `ContentBlockService` test patterns.
- Integration test: event → dispatcher → template resolve → email delivery, asserted via Mailpit
  (HTTP API or `GenericContainer`, per open question 3). Mirrors
  `common/src/test/java/.../bus/kafka/KafkaEventBusIntegrationTest.java`'s
  `@SpringBootTest` + `@Testcontainers` shape, adapted for the `integration` module boot (now with a
  real datasource, per decision 1).
- Regression: `KafkaEventBusIntegrationTest` and the existing `PingEventHandler` round-trip must
  still pass after re-enabling JPA in `integration` — confirms decision 1 doesn't break slice 1's
  proven boot path.

### Frontend
None this slice.

## Related features
- Dependencies: slice 1 (`EventBus`/`EventHandler` port, Kafka adapter) — closed, merged to `main`.
- Config data this slice reads: milestone 03's `NotificationType`/`NotificationTemplate`/
  `NotificationTemplateTranslation` (`NB-NOTIF-notifications-config.md`).
- Playbook extraction: once this slice lands, extract `async-consumer.md` and
  `notification-dispatch.md` from it (currently `[anticipated]` in `docs/playbooks/README.md`).

## Risks
- Re-enabling JPA in `integration` (decision 1) reverses a slice-1 fix made for a then-correct reason
  (worker was stateless). Regression risk: confirm the original "Failed to determine a suitable
  driver class" boot failure doesn't resurface — it was a **missing datasource config** issue as much
  as a JPA-exclusion one; the worker's `application.properties`/environment must supply real
  datasource credentials once the exclusion is removed (same as `app`/`admin` already do).
- Transport port introduced for a single concrete adapter (email) — justified by named future
  consumers (decision 2), but if Telegram/SMS scope changes before slice 4, this is a port with one
  implementation for longer than planned. Accepted risk, not a blocker.
- No Mailpit-Testcontainers precedent in this repo — first use of `GenericContainer` here if chosen;
  slightly more setup risk than reusing an established Testcontainers module.

## Slice 2 — CLOSED, as-built (2026-07-11)

Implemented per the locked decisions above, with the open questions resolved as follows (full
rationale in `docs/sources-log.md`, the four `04-integration-bus slice 2` rows):

1. **DB access**: exclusion line removed from `integration/application.properties` entirely (not
   moved to a local profile) — the worker has no stateless mode left after this slice.
   `spring.datasource.*`/`spring.mail.*` set directly in that base file via the repo's standard
   `${VAR:dev-default}` placeholder pattern.
2. **`NotificationEvent` package** → `common/.../notifications/` (open question 2), not `bus/`.
3. **Jackson dependency**: NOT classic `com.fasterxml.jackson.databind.ObjectMapper` as proposed —
   GATE-0 miss, caught at boot: Spring Boot 4.1 defaults to **Jackson 3**
   (`tools.jackson.databind.json.JsonMapper`, via `spring-boot-starter-json`); Jackson 2 support is
   deprecated. `common`'s own `dependency:tree` confirmed no Jackson bean was transitively available
   either way — this was a genuine "verify the library API before writing code" miss, not an
   ambiguity the plan anticipated. Fixed in `integration` only (common needed no direct dependency;
   `NotificationEvent` itself has no Jackson import).
4. **Mailpit test approach**: `GenericContainer("axllent/mailpit:latest")`, not the live `stack.yml`
   instance — hermetic, mirrors the repo's existing Kafka/Postgres Testcontainers convention.
5. **`isEnabled()`**: confirmed correct as proposed (Lombok `@Getter` on the `boolean` field).

**Naming, as built** (open question 1 — all matched the proposals, no changes):
`NotificationDispatchEventHandler`, `NotificationTransport`, `EmailNotificationTransport`, all in
`integration/.../dispatch/`. One deviation from the `PingEventHandler` precedent: the dispatcher is
a plain `@Component`, NOT `@ConditionalOnProperty(bus=kafka)` — broker-neutral per `EventHandler`'s
own contract, unlike `PingEventHandler` which is deliberately Kafka-adapter-specific proof-of-pipe
glue (see sources-log for the full reasoning).

**Files changed**:
- `common/.../notifications/NotificationEvent.java` (new record).
- `common/.../notifications/NotificationsService.java` — added `resolveForDispatch`.
- `common/src/test/.../notifications/NotificationsServiceTest.java` (new, 5 tests).
- `integration/.../dispatch/{NotificationTransport,EmailNotificationTransport,
  NotificationDispatchEventHandler}.java` (new).
- `integration/src/test/.../dispatch/NotificationDispatchEventHandlerIntegrationTest.java` (new).
- `integration/pom.xml` — added Lombok, `spring-boot-starter-mail`, `spring-boot-starter-json`,
  test deps (`spring-boot-starter-test`, `spring-boot-testcontainers`,
  `testcontainers-junit-jupiter`, `testcontainers-postgresql`).
- `integration/src/main/resources/application.properties` — removed the DataSource/JPA/Flyway
  exclusion, added datasource + mail properties.

**DoD met**: `mvn -B verify` green across the full reactor (66 common tests incl.
`KafkaEventBusIntegrationTest` regression-passing, 1 integration-module test — the full
event→resolve→email round trip against real Postgres + Mailpit containers).

## Slice 3 — bus-level retry + DLQ

**Scope**: backend only. Branch `04-retry-dlq`, cut from `main` (slices 1+2 merged).

**Applicable playbook**: `async-consumer.md` **[anticipated]** — "topic → handler → DLQ topic on
failure (no built-in requeue in Kafka)" is exactly this slice; per "extract, don't predict" it stays
unwritten until this slice lands, then gets extracted from it.

**Goal**: when a consumer handler fails, the bus retries N times with backoff, then routes the event
to a dead-letter topic instead of dropping it. Bus-level (Kafka redelivery → DLT), NOT a
transport-level retry loop inside the dispatcher.

**Recon basis**: two independent recons (Claude Code + Codex), compared in the originating chat — not
re-run here. Forks below are LOCKED from that comparison, not open for re-litigation at GATE-0.

### Precondition (fork 0 — a fact, not a design choice)
`KafkaEventBusAutoConfiguration`'s consumer factory (`common/.../bus/kafka/`, `~85-90`) is built via
`DefaultKafkaConsumerFactory` from a bare `Map`, so Spring never overrides the Kafka client default
`enable.auto.commit=true`. With auto-commit on, the consumer acks offsets on its own schedule
regardless of handler outcome — `DefaultErrorHandler`-driven redelivery has nothing to seek back to.
**Must set** `ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG=false` in the consumer factory's `Map`, and
`ContainerProperties.AckMode.RECORD` on the container — without both, retry cannot actually redeliver.
(One of the two source recons initially claimed the default was already `false`; the other caught the
mistake against the client's real default. Kept as a chat-level correction, not a `sources-log` row —
no code existed yet to log against.)

### Locked forks

1. **Terminal-vs-retriable classification → typed exceptions on the port**, in `common/.../bus/`:
   `RetriableBusException` / `TerminalBusException`. Rationale: delivery reliability (retry vs.
   give-up) is broker-neutral semantics, not a Kafka-only concept — a future RabbitMQ adapter needs
   the same signal from a handler. Rejected: Kafka-only exception classes declared in `integration`
   and referenced by the Kafka adapter's config — that leaks delivery semantics into the adapter and
   ties the port's failure contract to whichever concrete exception types the current handler happens
   to throw.
2. **Dispatcher stops swallowing.** `NotificationDispatchEventHandler` (currently drop + log at WARN
   on every failure path, per Slice 2's decision 6) throws instead:
   - **TERMINAL** → unparseable JSON, no template/translation match, disabled `NotificationType`. No
     retry — straight to DLT.
   - **RETRIABLE** → any transport (SMTP) failure. Retried with backoff, DLT after N attempts.
   - Fine-grained SMTP 4xx (permanent) vs 5xx (transient) distinction is **deferred** — any transport
     failure is retriable for now.
3. **Retry mechanism = `DefaultErrorHandler`** (blocking backoff on the consumer thread), attached via
   `setCommonErrorHandler` on the existing `ConcurrentMessageListenerContainer`
   (`KafkaEventBusAutoConfiguration`, ~line 110) — no restructuring of the container needed.
   `@RetryableTopic` is **structurally ruled out**: it requires `@KafkaListener`-annotated methods with
   a fixed `topics` attribute resolved at proxy-creation time, but this codebase's topic set is the
   runtime union of every registered `EventHandler` bean's `topic()` (locked reason recorded in
   `docs/sources-log.md:97`, re-confirmed by both recons) — adopting it would mean abandoning that
   design.
4. **DLT provisioning = auto-create.** No topic in this repo is explicitly declared anywhere (no
   `NewTopic` bean, no `stack.yml` topic provisioning) — every existing topic relies on Kafka's
   auto-create-on-first-use default. `DeadLetterPublishingRecoverer` publishes to `<topic>-dlt` (its
   default suffix convention) with no override needed. Redpanda Console (already in `stack.yml`, port
   8085) shows the DLT topic the same way it shows any other. Constraint: DLT partition count must be
   ≥ source topic's — fine, since dev topics are single-partition.
5. **Defaults = 2 retries (3 attempts total), `FixedBackOff(1000, 2)`** (1s interval), configured in
   `KafkaEventBusProperties` (runnable-owns-config precedent from slice 1). Exponential backoff is
   deferred.

### Code location
- `RetriableBusException`, `TerminalBusException` → new, `common/src/main/java/io/github/degdev/engine/common/bus/`.
- `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` bean, `addNotRetryableExceptions(TerminalBusException.class)`,
  `FixedBackOff(1000, 2)` → `common/.../bus/kafka/KafkaEventBusAutoConfiguration.java` (this is where
  the container is built; `integration` has no direct `spring-kafka` dependency, only transitive via
  `common`, per recon — a Kafka-specific bean belongs in the adapter module, not the consumer module).
- `ENABLE_AUTO_COMMIT_CONFIG=false` + `AckMode.RECORD` → same file, consumer factory `Map` +
  `ContainerProperties`.
- Retry defaults (max attempts, backoff interval) → `common/.../bus/kafka/KafkaEventBusProperties.java`.
- Dispatcher throws typed exceptions instead of swallowing → edit
  `integration/.../dispatch/NotificationDispatchEventHandler.java` (remove the `catch`+`LOG.warn`+
  `return` on all three failure paths — parse failure, no-template-match, transport failure — replace
  with throwing `TerminalBusException`/`RetriableBusException` as classified in fork 2).

### Files to create / change

Modules touched: `common`, `integration`.

- `common/src/main/java/io/github/degdev/engine/common/bus/RetriableBusException.java` — new.
- `common/src/main/java/io/github/degdev/engine/common/bus/TerminalBusException.java` — new.
- `common/src/main/java/io/github/degdev/engine/common/bus/kafka/KafkaEventBusAutoConfiguration.java` —
  add `ENABLE_AUTO_COMMIT_CONFIG=false` to the consumer factory map; set `AckMode.RECORD` on
  `ContainerProperties`; build `DefaultErrorHandler` (with `DeadLetterPublishingRecoverer` wrapping a
  `KafkaTemplate`, `FixedBackOff` from properties, `addNotRetryableExceptions(TerminalBusException.class)`)
  and attach via `setCommonErrorHandler` on the constructed container.
- `common/src/main/java/io/github/degdev/engine/common/bus/kafka/KafkaEventBusProperties.java` — add
  retry-attempt-count / backoff-interval-ms properties (defaults 2 / 1000).
- `integration/src/main/java/io/github/degdev/engine/integration/dispatch/NotificationDispatchEventHandler.java` —
  replace swallow-all drop+WARN with throwing typed exceptions per fork 2's classification.
- `common/src/test/java/io/github/degdev/engine/common/bus/kafka/KafkaEventBusIntegrationTest.java` —
  extend (or a new sibling test) with a handler that deliberately throws, asserting redelivery count
  and a record landing on `<topic>-dlt`.
- `docs/sources-log.md` — three new rows: the auto-commit default fix, the retry/DLQ mechanism
  decision, the port-level exception model decision.

### Open questions
None locked-open — all five forks above are locked from the source chat's recon comparison. The only
GATE-0-time verification (not a design choice): confirm `DeadLetterPublishingRecoverer`'s exact
constructor signature and `KafkaTemplate` bean availability against the installed `spring-kafka`
version before writing the autoconfiguration bean, rather than assuming the context7-recalled API
matches exactly.

### Testing strategy

**Backend**:
- Unit tests: `RetriableBusException`/`TerminalBusException` classification is exercised implicitly
  through the dispatcher's throw sites — a unit test per failure path (parse failure → terminal,
  no-template-match → terminal, disabled type → terminal, transport failure → retriable) on
  `NotificationDispatchEventHandler`, asserting the thrown exception type.
- Integration test (Testcontainers Kafka, extends `KafkaEventBusIntegrationTest`'s existing shape): a
  handler that always throws `RetriableBusException` → assert exactly 3 delivery attempts (1 + 2
  retries) → assert a record appears on `<topic>-dlt`. A second case: a handler that throws
  `TerminalBusException` → assert exactly 1 attempt (no retry) → assert straight-to-DLT.
- Regression: existing `KafkaEventBusIntegrationTest` (ping round-trip) and
  `NotificationDispatchEventHandlerIntegrationTest` (slice 2's happy-path email delivery) must still
  pass after the auto-commit/ack-mode change — proves the precondition fix doesn't break already-
  proven consumption.
- Proof the retry/DLQ test actually catches breakage: revert the `DefaultErrorHandler` wiring
  temporarily, confirm the new test fails, then reapply — same discipline slice 1 used for its Kafka
  round-trip test.

**Frontend**: none this slice.

### Out of scope (name only — do not design, do not build)
- Transport-level retry (a retry loop inside the dispatcher or `EmailNotificationTransport` itself).
- Telegram transport, SMS transport (named in the milestone's "Slice 3+" list, unrelated to retry/DLQ).
- Scheduler harness.
- Exponential backoff (fixed only this slice).
- SMTP 4xx/5xx fine-grained distinction (any transport failure = retriable, per fork 2).

### DoD
- [ ] `ENABLE_AUTO_COMMIT_CONFIG=false` + `AckMode.RECORD` set on the Kafka adapter's consumer/container.
- [ ] `RetriableBusException` / `TerminalBusException` exist in `common/.../bus/`; dispatcher throws
      typed exceptions instead of swallowing (no more blanket drop+WARN).
- [ ] `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` wired in the Kafka adapter:
      `FixedBackOff(1000, 2)`, `addNotRetryableExceptions(TerminalBusException.class)`.
- [ ] Retriable failure → 3 attempts → lands on `<topic>-dlt`. Terminal failure → straight to DLT, no
      retry.
- [ ] Testcontainers test proves both paths (retry-then-DLT, terminal-straight-to-DLT), including a
      revert-then-reapply proof that the test catches breakage. Builds on
      `KafkaEventBusIntegrationTest`.
- [ ] `mvn -B verify` green across the full reactor (existing slice 1/2 tests still pass).
- [ ] `docs/sources-log.md` rows added: auto-commit default fix, retry/DLQ mechanism decision,
      port-level exception model decision.

### Risks
- Setting `AckMode.RECORD` (per-record commit) instead of the container default (`BATCH`) changes
  commit frequency/throughput characteristics for every existing consumer on this bus (ping included),
  not just the dispatcher — acceptable for a dev-scale worker, but worth naming as a side effect of
  fixing the precondition, not an isolated dispatcher-only change.
- `DefaultErrorHandler`'s blocking backoff runs on the consumer thread — during the 1s×2 retry window
  for a stuck message, that partition's other messages don't get processed (head-of-line blocking).
  Accepted for this slice's scale; would need `@RetryableTopic`-style non-blocking retry if throughput
  becomes a concern later (structurally blocked this slice per fork 3, so not a near-term option).
- Typed exceptions on the port (`common/.../bus/`) are a broader-reaching contract than a Kafka-local
  fix — if a future adapter (RabbitMQ) can't cleanly map its own failure modes onto
  Retriable/TerminalBusException, this abstraction may need revisiting. Accepted per fork 1's
  reasoning (broker-neutral delivery semantics), not treated as a blocker now.

## Slice 3 — CLOSED, as-built (2026-07-11)

Implemented per the locked forks above with no deviations (full rationale in `docs/sources-log.md`,
the four `04-integration-bus slice 3` rows).

**Files changed**:
- `common/.../bus/RetriableBusException.java`, `common/.../bus/TerminalBusException.java` (new).
- `common/.../bus/kafka/KafkaEventBusProperties.java` — added `retryAttempts` (default 2) and
  `retryBackoffMs` (default 1000).
- `common/.../bus/kafka/KafkaEventBusAutoConfiguration.java` — consumer factory sets
  `ENABLE_AUTO_COMMIT_CONFIG=false`; `eventBusListenerContainer` sets `AckMode.RECORD` and attaches a
  `DefaultErrorHandler` (`DeadLetterPublishingRecoverer` over the existing `eventBusKafkaTemplate`,
  `FixedBackOff(retryBackoffMs, retryAttempts)`, `addNotRetryableExceptions(TerminalBusException.class)`)
  via `setCommonErrorHandler`.
- `integration/.../dispatch/NotificationDispatchEventHandler.java` — the three former drop+WARN
  branches (unparseable payload, no template match, transport failure) now throw
  `TerminalBusException`/`TerminalBusException`/`RetriableBusException` respectively.
- `common/src/test/.../bus/kafka/KafkaEventBusRetryDlqIntegrationTest.java` (new) — a handler that
  always throws `RetriableBusException` is redelivered exactly 3 attempts then dead-lettered; a
  handler that always throws `TerminalBusException` is dead-lettered on the first attempt with no
  retry. DLT topics observed by registering ordinary `EventHandler` beans for `<topic>-dlt`.
- `integration/src/test/.../dispatch/NotificationDispatchEventHandlerTest.java` (new) — unit tests for
  the dispatcher's four throw/no-throw paths (mocked `NotificationsService`/`NotificationTransport`).

**DoD met**: `mvn -B verify` green across the full reactor (common 68 tests incl. the new retry/DLQ
integration test; admin 53; integration 5, incl. the 4 new dispatcher unit tests). Revert-then-reapply
proof performed on the new retry/DLQ test: commenting out `container.setCommonErrorHandler(errorHandler)`
made both new test cases fail (`expected: "retry-me"/"terminal-me" but was: null`, confirmed by a live
`mvn test -Dtest=KafkaEventBusRetryDlqIntegrationTest` run); restoring the line turned them green again.
`docs/sources-log.md` gained the four rows listed above.

## Slice 4 — Telegram transport (send-only)

**Scope**: backend only. Branch `04-telegram-transport`, cut from `main` (slices 1-3 merged).

**Applicable playbook**: `notification-dispatch.md` **[anticipated]** — this slice is a direct
instance of "event → template (locale) → transport (email / SMS / Telegram)"; per "extract, don't
predict" it stays unwritten until a second transport actually exists behind the port, then gets
extracted from slices 2+4 together (email alone wasn't enough evidence for the generic pattern).

**Goal**: a `TelegramNotificationTransport` adapter behind the existing `NotificationTransport` port
that sends a message to a Telegram chat, mirroring `EmailNotificationTransport`'s shape. **Send-only.**

**Recon basis**: `nobilis-platform-back` recon (this session) + a locked-forks web discussion —
forks below are LOCKED, not open for re-litigation at GATE-0.

### Blocker (fork 1) — dispatcher transport selection
`NotificationDispatchEventHandler` currently injects a **single** `NotificationTransport` by type
(only `EmailNotificationTransport` implements the port — `integration/.../dispatch/
NotificationDispatchEventHandler.java:49`). Adding a second adapter as-is →
`NoUniqueBeanDefinitionException`. **Resolution — part of THIS slice, not deferred**: each adapter
declares its own `Transport transport()`; Spring injects `List<NotificationTransport>`; the
dispatcher builds a `Map<Transport, NotificationTransport>` from it and routes by
`event.transport()`. Mirrors the bus's own `handlersByTopic` precedent (`EventHandler` beans
collected into a map keyed by `topic()`). Adding a third transport (SMS) later touches zero
dispatcher code — proven by a test with two transports registered.

A `TELEGRAM` event arriving with no Telegram adapter mounted (bot token unconfigured) → the map has
no entry for `TELEGRAM` → dispatcher throws `TerminalBusException` → straight to DLT. Consistent with
slice 3's failure model (no silent drop).

### Locked forks

1. **Transport selection** — see blocker above. `Map<Transport, NotificationTransport>`, keyed by
   each adapter's own declared `transport()`.
2. **Recipient — no contract change.** `NotificationEvent.recipient` / the port's `recipient`
   parameter are already a generic `String` ("transport-specific address, e.g. an email address") —
   a Telegram chat_id flows through unchanged. **Account → Telegram-chat_id resolution is deferred to
   milestone 07** (domain knows which account has which Telegram chat); no producer resolves a
   chat_id today (confirmed: zero `chat_id` references anywhere in the repo pre-slice). This slice's
   own test supplies a chat_id directly — there are no real notification producers yet regardless of
   transport.
3. **Bot token is a separate token**, `nobilis.notification.telegram.bot-token` — conceptually a
   different bot from milestone 02's (deferred, code-free) Telegram LOGIN widget. Trivially merged
   into one bot later if that turns out to be desirable; not assumed now.
4. **Opt-in = `@ConditionalOnProperty` gate (crypto-style), NOT email's unconditional `@Service`.**
   Email has a working Mailpit default with no secret required; Telegram is useless without a bot
   token, so `TelegramNotificationTransport` mounts only when
   `nobilis.notification.telegram.bot-token` is set — mirrors `CryptoAutoConfiguration`
   (`@ConditionalOnProperty(prefix = "nobilis.crypto", name = "master-key")`), not
   `EmailNotificationTransport`'s plain `@Service`. A `TELEGRAM` event with the adapter absent behaves
   per the blocker section above (`TerminalBusException` → DLT), not a boot failure.
5. **HTTP client = `RestClient` POST to the Bot API**, not a bot library. `POST
   https://api.telegram.org/bot<token>/sendMessage` with `chat_id` + `text` (+ optional
   `parse_mode`), JSON body — confirmed current via context7 (Telegram Bot API docs) and Spring's
   `RestClient` reference (Spring Framework 6.2, the synchronous fluent client already implied by
   Spring Boot 4.1). Zero new deps, KISS; a full bot library's surface (updates, callbacks, commands)
   is unneeded for send-only and out of scope per the blocker/goal above.

### Failure classification
Distinguish, using Telegram's response (`ok`, `error_code`, `description`):
- **TERMINAL** (no retry, straight to DLT): 4xx-class errors — invalid `chat_id`, bot blocked by
  user, bad request. Same shape as slice 3's classification (`TerminalBusException`).
- **RETRIABLE** (retry then DLT): network failure, timeout, 5xx-class errors
  (`RetriableBusException`).
- No finer distinction than this is in scope — mirrors slice 3's own accepted-risk stance for SMTP
  (any transport failure lumped as retriable was the prior baseline; Telegram gets exactly this
  two-way split, not more).

### Config
Pattern = crypto `master-key`'s shape: `@ConfigurationProperties` + `@ConditionalOnProperty` +
`${NOBILIS_NOTIFICATION_TELEGRAM_BOT_TOKEN:}`-style placeholder in the runnable's
`application-local.properties.example` + real value never committed (gitleaks-gated).

**Gitleaks trap (recon-confirmed) — must fix THIS slice.** The existing custom rule
(`.gitleaks.toml`, `nobilis-base64-256bit-key`) only matches a 44-char Base64 value; a Telegram bot
token (`123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11` shape) does not match it and would rely solely on
gitleaks' generic built-in rule (unverified whether that catches this exact shape). **Add a
Telegram-token-shaped pattern to `.gitleaks.toml`** (digits `:` base64url-ish suffix) alongside the
existing key rule, allowlisting `${ENV}` placeholders and `*.example` files the same way.

### Code location
- `integration/.../dispatch/TelegramNotificationTransport.java` — new, `@Service` (or equivalent)
  `@ConditionalOnProperty(prefix = "nobilis.notification.telegram", name = "bot-token")`, implements
  `NotificationTransport`, adds `Transport transport()` returning `TELEGRAM`. `RestClient` field,
  `send(recipient, subject, body)` posts to `sendMessage` (recipient = chat_id; subject unused or
  folded into text, per Telegram having no subject concept — confirm at GATE-0 how `subject`+`body`
  map onto Telegram's single `text` field).
- `EmailNotificationTransport` — add its own `Transport transport()` returning `EMAIL` (both adapters
  now implement the same extended port shape).
- `NotificationTransport` port — add `Transport transport()` to the interface (both adapters must
  implement it; the port's `send(...)` signature is unchanged).
- `NotificationDispatchEventHandler` — replace the single injected `NotificationTransport transport`
  field with `List<NotificationTransport>` → build `Map<Transport, NotificationTransport>` (e.g. in a
  constructor or `@PostConstruct`), look up by `event.transport()`; missing entry →
  `TerminalBusException`.
- `common/.../notifications/` or a new `telegram/` config-properties class — bot-token
  `@ConfigurationProperties(prefix = "nobilis.notification.telegram")` (location: wherever
  `TelegramNotificationTransport` itself lives, mirrors `CryptoProperties`'s co-location with its
  autoconfig — confirm at GATE-0 whether this needs its own autoconfig class or a plain
  `@ConditionalOnProperty` on the `@Service` is sufficient, given it's a single bean not a Kafka-style
  adapter).
- `integration/pom.xml` — confirm `spring-boot-starter-web` (or whatever module supplies
  `RestClient`) is already on the classpath; add if not.
- `integration/src/main/resources/application.properties` — add
  `nobilis.notification.telegram.bot-token=${NOBILIS_NOTIFICATION_TELEGRAM_BOT_TOKEN:}` (empty
  default → adapter unmounted in dev unless explicitly configured).
- `admin/src/main/resources/application-local.properties.example` (or wherever the runnable's
  example file lives, mirrors crypto's) — add the placeholder line.
- `.gitleaks.toml` — new Telegram-token-shaped rule per the trap above.

### Files to create / change

Modules touched: `integration`, `common` (interface change only), root `.gitleaks.toml`.

- `integration/.../dispatch/NotificationTransport.java` — add `Transport transport()` to the
  interface.
- `integration/.../dispatch/EmailNotificationTransport.java` — implement `transport()` → `EMAIL`.
- `integration/.../dispatch/TelegramNotificationTransport.java` — new adapter (send-only), gated,
  `RestClient` POST to `sendMessage`, terminal/retriable classification per above.
- `integration/.../dispatch/NotificationDispatchEventHandler.java` — `Map<Transport,
  NotificationTransport>` selection replacing single-bean injection; missing-transport →
  `TerminalBusException`.
- `integration/pom.xml` — `RestClient`-supplying starter if not already present.
- `integration/src/main/resources/application.properties` — bot-token property.
- runnable's `application-local.properties.example` — placeholder line for the bot token.
- `.gitleaks.toml` — Telegram-token pattern + allowlist entries.
- `docs/sources-log.md` — rows for: the Map-based transport-selection refactor, the Telegram
  transport itself (RestClient choice, terminal/retriable mapping), the gitleaks pattern addition,
  the separate-token-scope decision.

### Open questions
1. How `subject` + `body` (the port's two text params) map onto Telegram's single `text` field —
   concatenate, drop subject, or use `parse_mode` formatting to distinguish them. Not locked; decide
   at GATE-0/implementation, doesn't affect the architecture above.
2. Whether the bot-token config needs its own `@ConfigurationProperties`/autoconfig class (crypto-
   style, two files) or a single `@ConditionalOnProperty` + `@Value`/`@ConfigurationProperties` on
   `TelegramNotificationTransport` itself is sufficient for one property. Pick the simpler shape
   unless a second Telegram-scoped property emerges.
3. Exact `RestClient` error handling idiom for turning a non-2xx response (or Telegram's `ok:false`
   body on a 200) into terminal-vs-retriable — `onStatus()` handlers vs. catching
   `RestClientResponseException` vs. inspecting the parsed body's `error_code`. Confirm against the
   installed Spring version at GATE-0 rather than assuming from context7's general docs.

### Testing strategy

**Backend**:
- Unit test: `NotificationDispatchEventHandler` with **two** registered `NotificationTransport` fakes
  (one `EMAIL`, one `TELEGRAM`) — proves routing by `event.transport()` and proves adding a transport
  doesn't require dispatcher changes (mirrors slice 3's dispatcher unit-test shape, mocked
  dependencies).
- Unit test: `NotificationDispatchEventHandler` with a `TELEGRAM` event and no Telegram transport
  registered → asserts `TerminalBusException`.
- Unit test: `TelegramNotificationTransport` itself, mocked `RestClient` (no real HTTP, no
  Testcontainers precedent needed — email has no adapter-level unit test either, only its Mailpit
  integration test; Telegram's unit test fills a gap email left, using a mocked/stubbed `RestClient`
  or a WireMock-style stub of the Bot API endpoint) — three cases: success (200, `ok:true`), terminal
  (4xx / `ok:false` with a terminal `error_code`), retriable (network exception / 5xx).

**Frontend**: none this slice.

### Out of scope (name only — do not design, do not build)
- Inline buttons, callback-query handling, bot commands → milestone 07 (domain "ПОЛУЧИТЬ→claim"
  flow).
- Telegram LOGIN (milestone 02, B1 widget) — different concern, untouched, no code exists for it
  either.
- Account → Telegram chat_id resolution (deferred to milestone 07, per fork 2).
- SMS transport (named in the milestone's original "Slice 3+" list, not this slice).
- Fine-grained Telegram error-code taxonomy beyond the terminal/retriable two-way split.

### DoD
- [ ] `NotificationTransport` port gains `Transport transport()`; both adapters implement it.
- [ ] `NotificationDispatchEventHandler` selects via `Map<Transport, NotificationTransport>` built
      from an injected `List<NotificationTransport>`; routes by `event.transport()`; a test with two
      transports registered proves adding one doesn't touch dispatcher logic.
- [ ] `TelegramNotificationTransport` (send-only) behind the port, `@ConditionalOnProperty`-gated on
      `nobilis.notification.telegram.bot-token`, declares `transport() == TELEGRAM`.
- [ ] `RestClient` POST to `sendMessage`; terminal (4xx / invalid chat_id / blocked) →
      `TerminalBusException`; retriable (5xx / network) → `RetriableBusException`.
- [ ] A `TELEGRAM` event with the adapter absent (no bot token configured) → `TerminalBusException` →
      DLT (proven by a unit test, per fork 1).
- [ ] Bot-token config: property + `${ENV}` placeholder + `.example` entry + a gitleaks pattern
      covering the Telegram token shape.
- [ ] Tests: Telegram send via mocked HTTP (success + terminal + retriable paths); dispatcher
      two-transport routing test; dispatcher missing-transport test.
- [ ] `mvn -B verify` green across the full reactor.
- [ ] `docs/sources-log.md` rows added: Map-based transport selection, Telegram transport (RestClient
      + classification), gitleaks pattern, separate bot-token scope decision.

### Risks
- The port's `send(recipient, subject, body)` shape was designed around email's subject+body split;
  Telegram has no subject concept, so this slice either drops `subject` silently or folds it into
  `text` — a slightly awkward fit that a future third transport (SMS, also subject-less) will
  re-confirm rather than resolve differently.
- `@ConditionalOnProperty` gating means the Telegram adapter is genuinely untested against a real
  bot/chat in dev by default (no Mailpit-equivalent local stack service) — first real send only
  happens once a real token is configured somewhere, likely staging/prod, not this slice's
  Testcontainers-free unit tests.
- Widening the `NotificationTransport` port's contract (adding `transport()`) touches
  `EmailNotificationTransport` too — a small but real edit to already-shipped slice-2 code, not a
  pure-addition change.

## Slice 4 — CLOSED, as-built (2026-07-11)

Implemented per the locked forks above with one correction beyond the plan text, caught during build
(full rationale in `docs/sources-log.md`, the five `04-integration-bus slice 4` rows):

1. **Dispatcher's blanket catch had to change.** `NotificationDispatchEventHandler.handle` previously
   caught every `Exception` from `transport.send(...)` and rewrapped it as `RetriableBusException` —
   which would have silently flattened `TelegramNotificationTransport`'s own terminal classification.
   Fixed: `RetriableBusException`/`TerminalBusException` are now caught first and rethrown as-is; only
   unclassified exceptions fall through to the generic retriable wrap (email's `MailException`s still
   land there unchanged).
2. **Bot-token property stays genuinely absent**, not an empty-string `${ENV:}` default like
   mail/db's — an empty string still counts as "present" to `@ConditionalOnProperty`, which would
   have mounted the adapter with a broken token. Relies on Spring's relaxed env-var binding
   (`NOBILIS_NOTIFICATION_TELEGRAM_BOT_TOKEN` → `nobilis.notification.telegram.bot-token`), same
   mechanism `nobilis.crypto.master-key` already uses — no `.example` placeholder needed since
   `integration` has no local-profile split (unlike `admin`/`app`).
3. **`RestClient.Builder`/test seam**: `TelegramNotificationTransport` gained a package-private
   `RestClient`-accepting constructor (alongside the `@Autowired` `@Value`-driven one) purely as a
   test seam for `MockRestServiceServer.bindTo(...)` — no production behavior change.
4. All other locked forks (Map-based selection, separate bot-token scope, `RestClient` over a bot
   library, terminal/retriable split) landed exactly as planned, no deviations.

**Files changed**:
- `integration/.../dispatch/NotificationTransport.java` — added `Transport transport()`.
- `integration/.../dispatch/EmailNotificationTransport.java` — implements `transport()` → `EMAIL`.
- `integration/.../dispatch/TelegramNotificationTransport.java` (new) — send-only adapter,
  `@ConditionalOnProperty`-gated, `RestClient` POST to `sendMessage`, terminal/retriable
  classification.
- `integration/.../dispatch/NotificationDispatchEventHandler.java` — `Map<Transport,
  NotificationTransport>` selection built from an injected `List<NotificationTransport>`; missing-
  transport → `TerminalBusException`; typed exceptions from a transport propagate unchanged.
- `integration/src/test/.../dispatch/TelegramNotificationTransportTest.java` (new) — `transport()`,
  success, terminal (4xx), retriable (5xx), via `MockRestServiceServer`.
- `integration/src/test/.../dispatch/NotificationDispatchEventHandlerTest.java` — constructor updated
  for `List<NotificationTransport>`; two new tests: two-transport routing, missing-transport terminal.
- `integration/pom.xml` — added plain `spring-web` (not the starter — headless worker, no embedded
  server needed).
- `integration/src/main/resources/application.properties` — documented (not assigned) the bot-token
  property.
- `.gitleaks.toml` — new `nobilis-telegram-bot-token` rule for the `<bot_id>:<secret>` shape.
- `docs/sources-log.md` — five new rows (Map-selection, Telegram transport, catch-block correction
  folded into the transport row, separate bot-token scope, gitleaks pattern).

**DoD met**: `mvn -B verify` green across the full reactor (common 68, admin/auth/app unchanged,
integration 11 tests — 6 dispatcher unit incl. the 2 new routing/missing-transport cases, 4 new
Telegram transport tests, 1 Mailpit integration test regression-passing). `gitleaks dir .` and
`gitleaks git --staged` both clean against the actual changeset (the only 4 findings in the working
tree are pre-existing, correctly-`*-local.properties`-gitignored local dev secrets, unrelated to this
slice). Spotless + Checkstyle clean.

## Slice 5 — SMS transport (Messaggio, send-only)

**Scope**: backend only. Branch `04-sms-transport`, cut from `main` (slices 1-4 merged, Telegram
live).

**Applicable playbook**: `notification-dispatch.md` **[anticipated]** — same status as slice 4; stays
unwritten until extracted from the full email+Telegram+SMS trio together (per "extract, don't
predict").

**Goal**: an `SmsNotificationTransport` adapter behind the existing `NotificationTransport` port that
sends a text message via Messaggio, mirroring `TelegramNotificationTransport`'s shape almost exactly
(structural clone, Bot API swapped for the Messaggio HTTP API). **Send-only, single vendor.**

**Recon basis**: `nobilis-platform-back` recon (this session, jetbrains-confirmed against the merged
Telegram code) + a locked-forks web discussion, itself validated clean-room against Deg's working pet
project (`/media/deg/GamesBig/www/homeservice-back/.../PhoneVerificationService.java`) for protocol
shape only — zero code ported (different package, different company, Compo-origin). Forks below are
LOCKED, not open for re-litigation at GATE-0.

### Recon corrections to slice 4's text (confirmed against merged code, apply to SMS — and note for
future doc-fix, see Risks)
- **No `.env.example`/`*.example` artifact exists anywhere in the repo.** Slice 4's own text above
  (`### Config`, `### Code location`) describes an `application-local.properties.example` placeholder
  that was never actually created — the as-built Telegram section confirms the real mechanism: the
  property key is left **entirely absent** from `application.properties`, with a comment explaining
  why (an empty default still satisfies `@ConditionalOnProperty`'s presence check). SMS mirrors the
  **as-built** mechanism, not slice 4's original (pre-build) text.
- **No `@ConfigurationProperties` class.** `TelegramNotificationTransport` ended up `@Value`-injecting
  the token directly in its constructor (`TelegramNotificationTransport.java:51-55`), not a separate
  properties class. SMS mirrors `@Value` injection for `login`, `sender-id`, and the two optional
  config values below.

### Decision — single-vendor direct adapter, not a two-layer gateway port
`SmsNotificationTransport` = the Messaggio adapter itself, directly behind `NotificationTransport`
(Messaggio's request shape built inline, values sourced from config). **Not** a second
`SmsGateway`/`SmsProvider` sub-port with Messaggio as one pluggable implementation. The pet project's
two-layer multi-vendor strategy (`SmsProvider`/`SmsCenterProvider`/`MtsSmsProvider`, DB-backed
provider config) fit a multi-vendor reality that doesn't exist here — one real gateway (Messaggio) is
the only vendor Deg has. Per "extract, don't predict" (`docs/playbooks/README.md`): the abstraction
gets added if/when a second vendor is actually integrated, not speculatively now.

### Locked forks
1. **Transport registration** — zero dispatcher changes. `SmsNotificationTransport` declares
   `transport() == SMS`; the existing `Map<Transport, NotificationTransport>` selection (built from
   `List<NotificationTransport>`, slice 4) picks it up automatically — this is exactly the "adding a
   third transport touches zero dispatcher code" case slice 4's test already proves.
2. **Subject: ignored.** `Transport.SMS`'s own Javadoc already documents "templates typically use
   body only (subject is ignored)" — a merged, engine-level design signal that matches Telegram's
   existing behavior (`send(recipient, subject, body)` receives `subject` but never reads it,
   `TelegramNotificationTransport.java:62-78`). SMS does the same: parameter present, unused.
3. **Country-code prefix — adapter-side config default.** `nobilis.notification.sms.country-code`
   (e.g. `373`, all-Moldova — tied to Deg's specific Messaggio account/plan, never hardcoded).
   Applied by the adapter to the raw `recipient` before building the request. Full E.164
   normalization / knowing which account has which real phone number is deferred to milestone 07
   (same deferral shape as slice 4's Telegram chat_id resolution) — this slice's own tests supply a
   bare local-format number and assert the prefixed result.
4. **Failure classification — HTTP-status-only, mirrors Telegram exactly.** 4xx →
   `TerminalBusException`; 5xx / network / timeout → `RetriableBusException`. `RestClient`
   default-throws `HttpClientErrorException`/`HttpServerErrorException`/`ResourceAccessException` on
   these (context7-confirmed against Spring Framework reference docs during recon). **No Messaggio
   response-body error-code parsing this slice** — context7 has zero indexed Messaggio API
   documentation, so there is no independently verifiable taxonomy of Messaggio's own
   permanent-vs-transient error codes to design against yet. Deferred to milestone 07, to be built
   from real observed responses rather than guessed.
5. **Gating — both `login` AND `sender-id` required.** Unlike Telegram's single-property gate,
   Messaggio needs two values to send anything; `@ConditionalOnProperty` requires both present
   (two-condition group, or a small `@ConditionalOnExpression`/composed-annotation — confirm exact
   Spring idiom at GATE-0) before `SmsNotificationTransport` mounts. Either value absent → adapter not
   mounted → an `SMS` event has no map entry → `TerminalBusException` → DLT, same failure shape as
   slice 4's Telegram-absent case.
6. **gitleaks — new dedicated rule for the Messaggio login.** The existing rules don't fit:
   `nobilis-base64-256bit-key` expects a 44-char Base64 value; `nobilis-telegram-bot-token` expects
   the `\d{8,10}:[A-Za-z0-9_-]{34,36}` shape. `Messaggio-Login` is an arbitrary unstructured
   string/username with no fixed detectable shape — a precise shape-regex isn't possible the way it
   was for Telegram's token. Add a keyword-gated rule (matches on the property-name keyword
   `messaggio`, not a value-shape regex), allowlisted the same way as the existing rules.

### Code location
- `integration/.../dispatch/SmsNotificationTransport.java` — new, `@Service`, gated on both
  `nobilis.notification.sms.messaggio.login` and `nobilis.notification.sms.messaggio.sender-id`
  present, implements `NotificationTransport`, `transport()` returns `SMS`. `RestClient` field built
  from `@Value`-injected `login`/`sender-id` (+ optional `base-url` override, default
  `https://msg.messaggio.com`, + `country-code`). `send(recipient, subject, body)` prefixes
  `recipient` with the configured country code, POSTs to `/api/v1/send` with header
  `Messaggio-Login: <login>` and body `{"recipients":[{"phone":<prefixed>}],"channels":["sms"],
  "sms":{"from":<sender-id>,"content":[{"type":"text","text":<body>}]}}`; terminal/retriable
  classification per fork 4.
- `NotificationTransport` port, `NotificationDispatchEventHandler` — **unchanged**, both already
  support N transports since slice 4 (fork 1).
- `integration/src/main/resources/application.properties` — document (not assign) the two Messaggio
  properties + the optional base-url/country-code, mirroring the Telegram bot-token comment
  (`application.properties:21-28`) explaining why they stay absent rather than empty-defaulted.
- `.gitleaks.toml` — new keyword-gated `messaggio`-login rule per fork 6, alongside the existing
  `nobilis-telegram-bot-token` rule.

### Files to create / change

Modules touched: `integration`, root `.gitleaks.toml`. `common`/`NotificationTransport` port
untouched (slice 4 already generalized it).

- `integration/.../dispatch/SmsNotificationTransport.java` — new adapter (send-only), dual-gated,
  `RestClient` POST to Messaggio, terminal/retriable classification, country-code prefixing.
- `integration/src/test/.../dispatch/SmsNotificationTransportTest.java` — new, mirrors
  `TelegramNotificationTransportTest.java` structurally (`MockRestServiceServer.bindTo(RestClient
  .Builder)` via a test-seam constructor).
- `integration/src/main/resources/application.properties` — Messaggio config, documented-absent.
- `.gitleaks.toml` — Messaggio keyword rule + allowlist entries.
- `docs/sources-log.md` — rows for: the Messaggio adapter (direct single-vendor decision vs. a
  gateway sub-port), the gitleaks keyword-rule choice (vs. a shape regex), the country-code config
  decision, the deferred-error-taxonomy call (no context7 Messaggio docs).

### Open questions
1. Exact Spring idiom for a two-property `@ConditionalOnProperty` gate (composed annotation with
   `@ConditionalOnProperty` array vs. `@ConditionalOnExpression`) — confirm at GATE-0 against the
   installed Spring Boot 4.1 API, don't assume from memory.
2. Optional `base-url` override property — needed only if Messaggio ever needs a per-environment
   endpoint (e.g. a sandbox API). Include the property with a hardcoded-default fallback; not a hard
   requirement, drop if it adds no real value at GATE-0.

### Testing strategy

**Backend**:
- Unit test: `SmsNotificationTransportTest` — `transport()` returns `SMS`; success (2xx, no throw);
  terminal (4xx → `TerminalBusException`); retriable (5xx → `RetriableBusException`); country-code
  prefix applied to the outgoing request body. Mirrors
  `TelegramNotificationTransportTest.java` case-for-case.
- No new dispatcher tests needed — slice 4's two-transport routing/missing-transport tests already
  cover the N-transport case generically; a third transport doesn't add new dispatcher behavior to
  verify.

**Frontend**: none this slice.

### Out of scope (name only — do not design, do not build)
- Multi-vendor SMS abstraction / a second gateway behind `SmsNotificationTransport` (per the
  single-vendor decision above — revisit only if a second vendor is actually integrated).
- DB-backed SMS provider config (the pet project's `SmsProviderEntity` — not ported).
- SMS-based login/auth flow (milestone 02/07 concern, unrelated to this send-only notification
  transport).
- Account → phone-number resolution (deferred to milestone 07, per fork 3).
- Parsing Messaggio's JSON response body for finer-grained error codes (deferred to milestone 07, per
  fork 4 — no context7 docs to design against yet).

### DoD
- [ ] `SmsNotificationTransport` behind the port, `transport() == SMS`, gated on both
      `nobilis.notification.sms.messaggio.login` and `...sender-id` present; either absent → adapter
      not mounted → an `SMS` event → `TerminalBusException` → DLT (consistent with slice 4's
      Map-selection + missing-transport model, no new dispatcher code needed).
- [ ] `RestClient` POST to `msg.messaggio.com/api/v1/send` with the `Messaggio-Login` header and the
      `recipients`/`channels`/`sms` JSON body shape; the configured country-code prefix applied to
      the recipient before sending.
- [ ] Terminal (4xx) → `TerminalBusException`; retriable (5xx / network) → `RetriableBusException` —
      same two-way split as Telegram, no Messaggio body-level error parsing this slice.
- [ ] Config: `login`, `sender-id`, `country-code` (+ optional `base-url`) as `@Value`-injected
      properties, keys left absent from `application.properties` with an explaining comment (mirrors
      the as-built Telegram pattern, not slice 4's original example-file text); a gitleaks
      keyword-gated rule for the Messaggio login property.
- [ ] Test: `SmsNotificationTransportTest` via `MockRestServiceServer` — success / terminal /
      retriable / country-code-prefix cases, mirroring `TelegramNotificationTransportTest`.
- [ ] `mvn -B verify` green across the full reactor.
- [ ] `docs/sources-log.md` rows added: Messaggio adapter (single-vendor decision), gitleaks keyword
      rule, country-code config decision, deferred-error-taxonomy call.

### Risks
- No context7-verifiable Messaggio API documentation exists — the request/response shape is trusted
  from a single pattern-only read of Deg's older pet-project code, not a live spec. If Messaggio's
  actual current API has drifted from that shape, this slice's happy-path test would pass against a
  wrong assumption; first real send (staging/prod, real credentials) is the actual validation,
  same accepted-risk shape as slice 4's untested-against-a-real-bot risk.
- Country-code prefixing bakes in a single-country (`373`) assumption at the adapter config layer,
  same shape as the pet project's hardcoded prefix, just moved to config — a genuinely
  multi-country recipient base would need real E.164-aware normalization upstream (milestone 07),
  not this slice's simple string-prefix.
- **Doc-fix owed, separate from this slice**: slice 4's `### Config`/`### Code location` text above
  (lines ~587-627) describes a `@ConfigurationProperties` class and an `application-local
  .properties.example` placeholder that don't match what was actually merged (the as-built section
  below it is correct). Not fixed here to keep this an append-only change — flag for a future
  doc-cleanup pass.

## Slice 6 — Scheduler harness (the LAST slice of milestone 04)

**Scope**: backend only. Branch `04-scheduler`, cut from `main` (slices 1-5 merged, SMS live).

**Applicable playbook**: `feature-optionality.md` **[anticipated]** — opt-in feature wiring, "a
capability is off until the host explicitly enables it." Closest match in the index; stays unwritten
until extracted from a fuller set of opt-in instances (crypto, Kafka bus, Telegram, SMS, ping-demo,
and now scheduling all already follow this shape in code, but the playbook itself hasn't been
written yet). No `crud-standard`/`import-export`/`async-consumer`/`integration-adapter` playbook fits
a periodic-job mechanism.

**Goal**: a GENERIC scheduled-job harness in the integration worker — the MECHANISM for periodic
jobs (boot-time opt-in scheduling + one demo job proving it runs live). **Zero domain sweeper
logic.** The `reserved_until` sweeper itself (real `Order` table, Pending→Available transition,
`GetState` reconciliation) is milestone 07 — this slice prepares nothing beyond the periodic-run
mechanism.

**Recon basis**: `nobilis-platform-back` recon (this session, jetbrains-confirmed against merged
code + context7-confirmed against Spring Boot 4.1) + a locked-forks web discussion, validated
clean-room against Deg's pet project (`ExpiredPushDeviceDeleteScheduler.java`, package
`dev.compo.b2b`) for the `@Scheduled(cron=...)` shape only — zero code ported. Forks below are
LOCKED, not open for re-litigation at GATE-0.

### Premise (recon-confirmed)
- `@EnableScheduling` is absent repo-wide — `IntegrationApplication.java:22-27` is a bare
  `@SpringBootApplication`. Zero `@Scheduled`/`reserved_until`/`sweeper` anywhere. Fully greenfield.
- The pet project gates its job **inside the method body** (`@Value` string check, bean and its
  `@Scheduled` registration always mount, cron always fires, the check only short-circuits the
  work). That diverges from every `nobilis-platform-back` opt-in precedent — crypto
  (`CryptoAutoConfiguration.java:33`), Kafka bus, Telegram (`TelegramNotificationTransport
  .java:46`), SMS (`SmsNotificationTransport.java:55`), ping-demo (`PingDemoRunner.java:29-31`) —
  which all gate at **boot time** via `@ConditionalOnProperty` so the bean never mounts when off.
  This slice uses the nobilis boot-time-gate convention, not the pet project's in-method check.

### Recon correction — no `AutoConfiguration.imports` entry for this slice
Confirmed: `common`/`auth`/`admin`/`app` are **library** modules other hosts opt into by dependency,
so their opt-in features are wired via `@AutoConfiguration` classes registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (e.g.
`CryptoAutoConfiguration`). `integration` is **not** consumed that way — it **is** the worker app
itself (`IntegrationApplication`), and every existing opt-in piece inside it (`PingDemoRunner`,
`TelegramNotificationTransport`, `SmsNotificationTransport`) is a plain `@Component`/`@Service` +
`@ConditionalOnProperty`, picked up directly by `IntegrationApplication`'s own component scan — no
imports file, no `integration` module has ever had one. The scheduler mirrors that: a plain
`@Configuration` class (not `@AutoConfiguration`), same boot-time gate, same scan-pickup mechanism.
No new `META-INF/spring/...` file this slice.

### Locked forks
1. **Abstraction level — THIN.** `@EnableScheduling` + ONE demo `@Scheduled` job. No
   `ScheduledJob`-interface/registry. The `List<NotificationTransport>` auto-collection precedent
   (`NotificationDispatchEventHandler.java:63-74`) was introduced with 2+ real transports already in
   hand; the scheduler has **zero** real jobs today, so a registry would be speculation ahead of
   milestone 07 (extract-don't-predict, `docs/playbooks/README.md:10-16`). If/when milestone 07 adds
   a second job, the registry gets extracted then, from real use — not predicted now.
2. **Opt-in — `@ConditionalOnProperty` boot-time gate.** Matches crypto/Kafka/Telegram/SMS/ping. Not
   always-on (breaks every optional-feature convention in this repo); not the pet project's
   in-method runtime flag.
3. **`reserved_until` — mechanism only, sweeper shape deferred.** This slice ships the periodic-run
   mechanism (a job runs on a schedule, config-driven, gated). The "scan rows past a timestamp → act"
   shape stays undesigned until milestone 07, where the real `Order` table + `reserved_until` column
   + claim state machine exist to design it against. No generic sweeper base now — no second
   consumer to extract one from.
4. **Demo job — yes, tick-logging, gated off by default.** A scheduler with zero live jobs has
   nothing to prove running; unlike a transport (provably correct via a unit test alone), the
   harness's *live* wiring (does `@EnableScheduling` actually activate, does the cron/fixedDelay
   actually fire) has no other proof short of running it. Mirrors `PingDemoRunner`'s "test-only,
   off by default" shape. Paired with a deterministic unit test that invokes the job method
   **directly** — no Awaitility anywhere in this repo, don't wait for a real cron tick.
5. **Enablement location — a dedicated `SchedulingConfiguration`.** `@Configuration` +
   `@EnableScheduling`, gated by the same `@ConditionalOnProperty` as the demo job (or a slightly
   broader "scheduling enabled" flag gating the config class, with the demo job additionally gated
   on its own enabled flag — confirm exact two-flag-vs-one-flag shape at GATE-0). Not
   `@EnableScheduling` directly on `IntegrationApplication` (keeps the worker's main class free of
   opt-in feature annotations, consistent with how Telegram/SMS/ping-demo are separate classes, not
   inlined into the main class).

### Code location
- `integration/.../schedule/SchedulingConfiguration.java` — new, `@Configuration`,
  `@EnableScheduling`, `@ConditionalOnProperty(prefix = "nobilis.integration.scheduling", name =
  "enabled")`. Package-scanned automatically by `IntegrationApplication` (no imports file, per the
  recon correction above).
- `integration/.../schedule/DemoTickJob.java` (naming TBD at GATE-0) — new, `@Component`,
  `@ConditionalOnProperty(prefix = "nobilis.integration.scheduling.demo-tick", name = "enabled")`,
  one `@Scheduled(cron = "${nobilis.integration.scheduling.demo-tick.cron}")` (or `fixedDelay`,
  confirm at GATE-0 per context7's virtual-threads note) method that logs a tick. Off by default,
  same two-property "off unless a real value is configured" shape as SMS's dual gate.
- `integration/src/main/resources/application.properties` — document (not assign, or assign a safe
  default cron with `enabled` left absent) the scheduling + demo-tick properties, mirroring the
  Telegram/SMS comment convention.
- `integration/src/test/.../schedule/DemoTickJobTest.java` — new, calls the job method directly,
  asserts the tick happened (log capture or a simple counter/flag), no cron wait, no Awaitility.

### Files to create / change

Module touched: `integration` only. No `common`/other-module changes — this is worker-internal
wiring, not a new library port.

- `integration/.../schedule/SchedulingConfiguration.java` — new, `@EnableScheduling`, boot-time
  gated.
- `integration/.../schedule/DemoTickJob.java` — new, gated demo `@Scheduled` job.
- `integration/src/test/.../schedule/DemoTickJobTest.java` — new, direct-invocation unit test.
- `integration/src/main/resources/application.properties` — scheduling + demo-tick config keys.
- `docs/sources-log.md` — rows for: scheduling enablement pattern (context7 citation), the
  boot-time-gate decision vs. the pet project's in-method gate, the thin-not-registry decision, the
  demo-job-gated-off-by-default decision.

### Open questions
1. One enabled-flag (scheduling itself always mounts when the module is enabled, demo job has its
   own separate flag) vs. two independent flags from the start — confirm the minimal shape at
   GATE-0; default to mirroring SMS's "every required property must be present" gate style if a
   single flag suffices for both.
2. `cron` vs `fixedDelay` for the demo job — context7 confirmed Boot 4.1's virtual-thread scheduler
   (`spring.threads.virtual.enabled`, not currently set in this repo) recommends cron/fixed-rate over
   fixed-delay when active; since virtual threads aren't enabled here, either works — pick whichever
   is simpler to test deterministically at GATE-0.

### Testing strategy

**Backend**:
- Unit test: `DemoTickJobTest` — invoke the job method directly, assert the tick side effect
  happened (no live scheduling, no waiting for a real trigger).
- No integration test asserting `@EnableScheduling` actually fires a live cron tick — not this
  repo's convention (no Awaitility present), and the mechanism itself is a well-established Spring
  Framework feature (context7-confirmed), not something this slice needs to re-prove at the
  framework level.

**Frontend**: none this slice.

### Out of scope (name only — do not design, do not build)
- Domain sweeper logic (the real `reserved_until` scan/reclaim job) — milestone 07.
- `Order` reclaim, Pending→Available state transition — milestone 07.
- `reserved_until` column on any real entity — milestone 07 (no such entity exists yet).
- `GetState`/bank reconciliation — milestone 07.
- A `ScheduledJob` registry abstraction — deferred until a second real job exists (per locked fork 1).

### DoD
- [ ] `SchedulingConfiguration`: `@EnableScheduling`, `@ConditionalOnProperty`-gated (boot-time), so
      scheduling mounts only when enabled — plain `@Configuration` picked up by
      `IntegrationApplication`'s component scan, no `AutoConfiguration.imports` entry (worker-app
      wiring, not a library port).
- [ ] One demo `@Scheduled` job (config-driven cron/fixedDelay), gated off by default, that logs a
      tick.
- [ ] Config: cron/interval + enable-flag property names (runnable-owns-config); demo job does not
      run in prod unless explicitly enabled.
- [ ] Deterministic test: invokes the demo job method directly, asserts it did its work — no
      cron-tick wait, no Awaitility.
- [ ] Worker still boots headless with JPA (existing behavior intact); scheduling absent (no
      `TaskScheduler`/`@Scheduled` registration) when the flag is off.
- [ ] `mvn -B verify` green across the full reactor.
- [ ] `docs/sources-log.md` rows added: scheduling enablement (context7 citation), boot-time-gate
      decision vs. the pet project's in-method gate, thin-not-registry decision, demo-job-gated-off
      decision.

### Risks
- The demo job proves the harness mechanism, not any real periodic work — if milestone 07's sweeper
  ends up needing a shape this harness doesn't anticipate (e.g. distributed-lock coordination across
  multiple worker instances, which nothing here addresses), the harness may need extension rather
  than being a drop-in fit. Accepted: this slice is deliberately mechanism-only, per "extract, don't
  predict."
- No generic sweeper base means milestone 07 designs its scan-and-act shape from scratch against the
  real `Order` table — this slice does not reduce that design work, only the scheduling-activation
  boilerplate around it.
