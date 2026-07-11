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
