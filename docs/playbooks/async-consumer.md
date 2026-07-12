# Async consumer — nobilis-platform-back

**Type:** reusable playbook (wiring recipe) &middot; **Scope:** backend only (JVM / Spring Boot +
Kafka wiring — no frontend counterpart) &middot; **Status:** ready (extracted from six real
instances) &middot; **Updated:** 2026-07-12

**Reference (real files this playbook is extracted from):**

- **Port** — `common/.../bus/EventBus.java`, `common/.../bus/EventHandler.java`,
  `common/.../bus/RetriableBusException.java`, `common/.../bus/TerminalBusException.java`.
- **Kafka adapter** — `common/.../bus/kafka/KafkaEventBusAutoConfiguration.java`,
  `common/.../bus/kafka/KafkaEventBusProperties.java`.
- **Ping handler** (slice 1, proof-of-pipe) — `integration/.../ping/PingEventHandler.java`.
- **Notification dispatcher** (slice 2/3/4) — `integration/.../dispatch/
  NotificationDispatchEventHandler.java`, `integration/.../dispatch/NotificationTransport.java`.
- **Three real transports proving the plug-in shape** — `integration/.../dispatch/
  EmailNotificationTransport.java`, `TelegramNotificationTransport.java`,
  `SmsNotificationTransport.java`.
- **Test** — `integration/src/test/.../dispatch/NotificationDispatchEventHandlerTest.java`.

Provenance and rationale: `docs/sources-log.md` — the **04-integration-bus** rows for slices 1-5
(ping, dispatcher, retry+DLQ, Telegram, SMS). This file is the WIRING recipe extracted from them; it
does not restate the per-slice decisions.

## WHEN to apply

Adding a new **async consumer** in the `integration` worker — a handler that reacts to an event
arriving on the bus — or a new **delivery adapter** plugging into an existing dispatcher (a new
`NotificationTransport`, or the same shape for a future non-notification fan-out). This is the
template for all async work in this engine: notifications today, moderation/LLM jobs later
(`docs/playbooks/README.md`'s own framing). It does **not** cover *what* a notification handler
resolves before it dispatches (event → template → transport selection logic) — that is
`notification-dispatch.md` **[anticipated]**, staying unwritten until milestone 07 gives it a second
real event-type family to extract from.

## What it is (and why it is not obvious)

The worker never depends on Kafka directly in its own handler/adapter code — it depends on two
broker-neutral ports (`EventBus`/`EventHandler`) and two broker-neutral failure types
(`RetriableBusException`/`TerminalBusException`). Only one file in the whole repo,
`KafkaEventBusAutoConfiguration`, knows Kafka exists. Getting a new consumer or adapter right means
respecting that seam — three parts, plus a boot-time precondition that silently breaks retry if
missed.

## The shape

### 1. The port — depend on `EventHandler`/`EventBus`, never on a broker type

A consumer is a `@Component implements EventHandler`, declaring `topic()` and `handle(String)`.
Nothing in its code, imports, or dependencies mentions Kafka.

- `EventHandler.java:23-38` — the two-method broker-neutral contract, Javadoc: "Never depends on a
  broker-specific type."
- `PingEventHandler.java:30,36-44` — the minimal real instance: `topic()` returns a constant,
  `handle()` just logs.
- `NotificationDispatchEventHandler.java:55,76-79` — the real instance with actual work: `topic()`
  returns `nobilis.notifications.dispatch` (`:57`), `handle()` does the parse→resolve→deliver
  sequence.

**WHY broker-neutral:** exactly one bus adapter is active at a time, selected by
`nobilis.integration.bus` (`KafkaEventBusAutoConfiguration.java:69`). A handler written against
`EventHandler` keeps working unchanged if that adapter is ever swapped (a future RabbitMQ adapter,
named as the standing example in `EventBus.java:21`, `RetriableBusException.java:22`,
`TerminalBusException.java:22`).

### 2. The topic set is a runtime union, not a static list — no `@KafkaListener`

The active adapter discovers **every** `EventHandler` bean present at boot and builds the consumed
topic set from their declared `topic()` values; it does not use `@KafkaListener`.

- `KafkaEventBusAutoConfiguration.java:105-116` — `eventBusListenerContainer` takes
  `List<EventHandler> handlers`, groups them `Collectors.groupingBy(EventHandler::topic)`
  (`:112-113`), and builds a raw `ContainerProperties` from the resulting topic set (`:115-116`) —
  not an annotation-declared topic.
- The listener container bean itself is `@ConditionalOnBean(EventHandler.class)`
  (`KafkaEventBusAutoConfiguration.java:106`) — no handler registered anywhere → no container at all,
  not an empty one.

**WHY not `@KafkaListener`:** context7-confirmed at slice-3 build time (`docs/sources-log.md`, slice
3 retry+DLQ row) — `@KafkaListener`'s `topics` attribute is fixed at proxy-creation time, which is
incompatible with a topic set that is the dynamic union of whatever `EventHandler` beans a given
worker happens to have mounted (notifications now, moderation/LLM later, each independently
opt-in-able). A raw `ConcurrentMessageListenerContainer` keeps topic discovery data-driven.

### 3. Failure classification lives on the broker-neutral port, not in the Kafka adapter

A handler signals **retriable** vs. **terminal** by throwing one of two typed exceptions defined in
`common/.../bus/`, never a Kafka-specific type.

- `RetriableBusException.java:24` / `TerminalBusException.java:24` — both `extends RuntimeException`,
  both Javadoc'd "Broker-neutral: carries no Kafka-specific meaning."
- `NotificationDispatchEventHandler.java:84-113` — real usage: unparseable payload → `Terminal`
  (`:87`); no matching enabled template → `Terminal` (`:93-96`); no registered transport for the
  event's channel → `Terminal` (`:99-101`); any other transport delivery failure → `Retriable`
  (`:110-113`); a transport's **own** already-classified exception (e.g. Telegram's 4xx-vs-5xx split)
  is re-thrown as-is, not flattened into the generic retriable fallback (`:106-109`).

**WHY on the port, not the adapter:** delivery reliability (retry-or-give-up) is broker-neutral
semantics — a future RabbitMQ adapter behind the same port needs the identical signal from a
handler. Keeping the types Kafka-adapter-local would tie the port's failure contract to whichever
concrete exception types today's one handler happens to throw (`docs/sources-log.md`, slice 3 row on
this exact decision).

### 4. The adapter wires retry + DLQ from that classification — with a precondition that silently breaks it if skipped

- `KafkaEventBusAutoConfiguration.java:128-133` — `DefaultErrorHandler` built from a
  `DeadLetterPublishingRecoverer` (`:130`, publishes to the built-in `<topic>-dlt` suffix) and a
  `FixedBackOff(retryBackoffMs, retryAttempts)` (`:131`, sourced from
  `KafkaEventBusProperties`, `KafkaEventBusProperties.java:26-30`, defaults `retryAttempts=2`,
  `retryBackoffMs=1000`); `addNotRetryableExceptions(TerminalBusException.class)` (`:132`) is what
  makes a terminal failure skip straight to the DLT — every other exception, including
  `RetriableBusException`, retries by default.
- **Precondition, easy to miss:** `ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false`
  (`KafkaEventBusAutoConfiguration.java:101`) plus `containerProperties.setAckMode(ContainerProperties
  .AckMode.RECORD)` (`:117`). Without both, the consumer acks offsets on its own timer regardless of
  handler outcome, and `DefaultErrorHandler` has nothing left to seek back to — redelivery silently
  does nothing. Caught during slice-3 recon before any code was written (`docs/sources-log.md`, slice
  3 row: "two independent recons compared, one initially misremembered the default as already
  `false`").

## The plug-in pattern — a second/third adapter is a bean, zero core-class edits

`NotificationDispatchEventHandler` depends on `List<NotificationTransport>`, not one concrete
transport, and builds its own routing map in the constructor.

- `NotificationTransport.java:29-46` — the delivery-side port: `send(recipient, subject, body)` +
  `transport()` declaring which `Transport` channel the adapter handles.
- `NotificationDispatchEventHandler.java:63-73` — constructor takes `List<NotificationTransport>
  transports`, collects them via `Collectors.toUnmodifiableMap(NotificationTransport::transport,
  Function.identity())` into `transportsByChannel`.
- `NotificationDispatchEventHandler.java:98-101` — routing is a map lookup by `event.transport()`; no
  registered adapter for that channel → `TerminalBusException`, the same failure shape as any other
  unresolvable event (not a boot-time crash, not a silent drop).
- **Three real instances proving "add N, touch zero dispatcher code":** `EmailNotificationTransport
  .java:30-32,46` (unconditional `@Service`, the slice-2 baseline — the *only* one of the three with
  no gate, since Mailpit gives it a working dev default), `TelegramNotificationTransport
  .java:45-47,81` (slice 4, second adapter — dispatcher required **zero** changes to add it, per
  `docs/sources-log.md`'s slice 4 row), `SmsNotificationTransport.java:54-58,114` (slice 5, third
  adapter — same zero-dispatcher-change claim, proven generically rather than re-tested, per
  `docs/sources-log.md`'s slice 5 testing-strategy note).
- Test proof: `NotificationDispatchEventHandlerTest.java:132-133`
  (`routesToTheMatchingTransportWhenTwoAreRegistered`) and `:118-119`
  (`channelWithNoRegisteredTransportIsTerminal`).

**WHY `List<X>` + a constructor-built map, not a registry class:** Spring already auto-collects every
bean implementing an interface into an injected `List`; no custom registry/manager type earns its
keep for a plug-in point this simple. (Named explicitly as the precedent — and explicitly **not**
generalized further without a second real consumer — in the scheduler-harness slice's
`docs/sources-log.md` row on its own thin-not-registry decision.)

## The opt-in convention — every optional adapter gates at boot time, never inside the method

Each adapter that isn't universally safe to run mounts via `@ConditionalOnProperty`; none of them
gate with a runtime flag checked inside the handler body.

- `PingEventHandler.java:28-29` — `@ConditionalOnProperty(prefix = "nobilis.integration", name =
  "bus", havingValue = "kafka")`: a slice-1 proof-of-pipe consumer, only mounted when Kafka is
  selected.
- `KafkaEventBusAutoConfiguration.java:68-69` — the adapter itself is
  `@ConditionalOnProperty(prefix = "nobilis.integration", name = "bus", havingValue = "kafka")`.
- `TelegramNotificationTransport.java:45-46` — single-property gate (`bot-token` present).
- `SmsNotificationTransport.java:54-58` — **two-property** gate (`login` **and** `sender-id` both
  required — `@ConditionalOnProperty(prefix = ..., name = {"login", "sender-id"})`); either absent →
  not mounted.
- **Consequence when a channel has no mounted adapter:** the dispatcher's own map lookup returns
  `null` → `TerminalBusException` (`NotificationDispatchEventHandler.java:99-101`) → DLT. Never a
  boot-time failure, never a silent drop — an unmounted adapter and an unresolvable event are the
  same failure path.

**WHY boot-time, not in-method:** a bean that always mounts and checks an on/off flag inside its own
method still registers itself (with Kafka: still occupies a topic-consumer slot; with a
`@Scheduled` job: the schedule still fires, the check only short-circuits the work). Every optional
adapter in this repo instead mounts *or doesn't* — gate on presence of the config that makes the
adapter meaningful (a token, a login+sender pair), not a separate boolean flag layered on top.

## Gotchas actually hit (recurring, not one-offs)

- **Spring Boot 4.1 defaults to Jackson 3, not classic Jackson 2.** `NotificationDispatchEventHandler
  .java:31,61,85` uses `tools.jackson.databind.json.JsonMapper`, not
  `com.fasterxml.jackson.databind.ObjectMapper` — the latter isn't auto-configured by plain
  `spring-boot-starter` in Boot 4.1. Fixed by adding `spring-boot-starter-json`
  (`integration/pom.xml:56-59`) rather than the classic Jackson 2 starter. context7-confirmed at
  slice-2 build time (`docs/sources-log.md`, slice 2 Jackson row) — check context7 for the *current*
  JSON stack before writing bus-payload (de)serialization against this repo's newest-LTS Boot, same
  rule already in force for Hibernate 7 / Angular 22.
- **`integration` has no direct `spring-kafka` dependency** — `integration/pom.xml` (checked: no
  `spring-kafka`/`spring-boot-starter-kafka` entry) pulls Kafka only **transitively** via `common`
  (which owns `KafkaEventBusAutoConfiguration`). A new consumer in `integration` should keep depending
  on `EventHandler`/`EventBus` from `common`, never add a direct Kafka dependency of its own — that
  would reintroduce the broker coupling the port exists to prevent.
- **Each secret-shaped optional-adapter property gets its own gitleaks rule, not a shared one.**
  `.gitleaks.toml:14-16` (`nobilis-base64-256bit-key`, a 44-char Base64 shape — crypto's master key)
  does **not** match a Telegram bot token's `\d{8,10}:[A-Za-z0-9_-]{34,36}` shape, which needed its
  own rule (`.gitleaks.toml:25-27`, `nobilis-telegram-bot-token`); Messaggio's login is an
  unstructured string with no detectable shape at all, so it needed a **keyword**-gated rule instead
  of a value-shape regex (`.gitleaks.toml:35-38`, `nobilis-messaggio-login`). Adding a new adapter
  with its own secret-shaped config property means checking whether an existing rule actually matches
  that shape — do not assume the crypto rule covers it.
- **A transport that has no subject concept still receives the `subject` parameter — and ignores it,
  rather than dropping it from the port.** `TelegramNotificationTransport.java:63` and
  `SmsNotificationTransport.java:85` both implement `send(String recipient, String subject, String
  body)` and never read `subject` — the port stays uniform across transports (some use both fields,
  some only body) instead of Telegram/SMS getting a narrower interface. Documented at the `Transport`
  enum constant itself, not just the adapter (`Transport.java`, `TELEGRAM`'s own Javadoc: "Templates
  typically use body only (subject is ignored)").
- **`nobilis.integration.bus.kafka.bootstrap-servers` has no `@DefaultValue`
  (`KafkaEventBusProperties.java:27`) — opting into `nobilis.integration.bus=kafka` without it also set
  does not fail fast.** Spring's relaxed record binding happily constructs the properties record with
  `bootstrapServers = null`; that null then reaches `new DefaultKafkaProducerFactory<>(configs)`
  (`KafkaEventBusAutoConfiguration.java:79`), whose internal `new ConcurrentHashMap<>(configs)` copy
  rejects the null value and throws a **message-less** `NullPointerException` several frames deep —
  the outer `BeanCreationException` reads "threw exception with message: null", which gives no hint
  that a property is missing. Both properties are mandatory together; there is no working single-flag
  opt-in.
- **The local dev broker (`stack.yml`'s `kafka` service) exposes two listeners with different
  audiences — pointing at the wrong one connects but then dies on metadata.** `PLAINTEXT://kafka:9092`
  is advertised for containers on the compose network only; `PLAINTEXT_HOST://localhost:29092` is the
  one host-machine clients (a locally run `IntegrationApplication`) must use. Bootstrapping against the
  internal port still succeeds (it's port-mapped), but the broker's metadata response then advertises
  the internal `kafka` hostname, which the host cannot resolve — surfaces as a `NetworkClient`
  `UnknownHostException: kafka` retry loop *after* the app has already started successfully. Not fixed
  by editing `/etc/hosts` or the compose file; the client-side property must target the host listener.

## The async-consumer checklist (for the NEXT handler or transport adapter)

1. **Handler** — `@Component implements EventHandler`, `topic()` returns a constant, `handle()` never
   imports anything Kafka-specific.
2. **Failure signal** — throw `TerminalBusException` for anything redelivery cannot fix (bad payload,
   no matching config/template, no registered downstream adapter); throw `RetriableBusException` (or
   let an unclassified exception fall through to a generic retriable wrap, per
   `NotificationDispatchEventHandler.java:110-113`) for anything transient.
3. **A new delivery adapter** (transport, or an equivalent plug-in point) — implement the relevant
   port interface, declare its own selector method (`transport()` or equivalent); inject it into the
   consumer as `List<X>`, never widen the consumer's constructor to a named concrete type.
4. **Gate at boot time**, not inside the method — `@ConditionalOnProperty` on whichever config values
   make the adapter meaningful; list every required property together
   (`name = {"a", "b"}`) if more than one is needed, don't build a composed always-on bean with an
   internal check.
5. **Don't add a registry/manager class for a plug-in point Spring's `List<X>` auto-collection
   already covers** — introduce one only once a second real generalization need proves the simple
   shape insufficient (see the scheduler-harness precedent above).
6. **Check gitleaks** — does an existing rule's shape actually match the new secret-shaped property,
   or does it need its own rule (value-shape regex if the secret has a fixed shape, keyword-gated if
   it doesn't)?
7. **Test the handler/adapter directly**, not through a live broker round-trip — call `handle()` /
   `send()` directly and assert on the typed exception or side effect
   (`NotificationDispatchEventHandlerTest.java`, `TelegramNotificationTransportTest.java` via
   `MockRestServiceServer`). The bus→handler wiring itself (publish → Kafka → listener container →
   registered `handle()`) is already proven generically — re-testing it per-handler duplicates
   coverage without adding signal.

## When NOT to force

- **Resolving *what* to send** (event → template → transport selection, locale fallback, interpolation)
  is not this playbook — that is `notification-dispatch.md` **[anticipated]**, staying unwritten
  until a second real event-type family (beyond notifications) exists to extract the general shape
  from.
- **A periodic/scheduled job with no incoming event** is not this pattern — that's the
  `nobilis.integration.scheduling.*` opt-in shape (`SchedulingConfiguration`,
  `integration/.../schedule/`), which reuses this playbook's boot-time-gate convention but has no bus
  port, no topic, no retry/DLQ concept.
- **A registry/manager abstraction for the plug-in point** — rejected here on purpose while there are
  only 2-3 real implementations per port; `List<X>` + a constructor-built map already gives "add N,
  touch zero core code" with no extra machinery.

## Links

- **Rationale / provenance** — `docs/sources-log.md`, the **04-integration-bus** rows for slices 1
  (ping/Kafka adapter), 2 (Jackson 3), 3 (retry+DLQ, failure-type placement), 4 (Telegram, transport
  `Map`), 5 (SMS, gitleaks rules).
- **Resolve logic** (event → template → transport) — `docs/playbooks/notification-dispatch.md`
  **[anticipated]**.
- **Boot-time opt-in gating, generalized beyond the bus** — the same convention is used by
  `CryptoAutoConfiguration` (`common/.../crypto/`) and the scheduler harness
  (`integration/.../schedule/SchedulingConfiguration.java`); this playbook documents its async/bus
  instance specifically.
