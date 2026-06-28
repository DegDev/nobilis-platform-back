# Sources Log — nobilis-platform

Provenance trail: for every non-trivial decision — the public pattern/standard/source it derives
from. Evidence of independent clean-room creation. Appended to as we go.

| Decision | Source / rationale |
|---|---|
| Project name `nobilis` | Ubuntu 24.04 LTS "Noble Numbat" (the release the machine was on the day work started). |
| groupId `io.github.degdev` | Verified namespace on Maven Central via the GitHub account DegDev (free, no domain; cannot be taken). Engine root package `io.github.degdev.engine.*`. `dev.nobilis` rejected — it required a paid/taken `nobilis.*` domain. |
| Java 25 via SDKMAN + `.sdkmanrc` (per-project, NOT global) | Other projects on the machine run on JDK 17 in parallel. We don't touch the global default: `.sdkmanrc` (`java=25-tem`) in the repo root + `sdkman_auto_env=true` switch JDK per folder. `.sdkmanrc` is committed → reproducibility for any clone. |
| Java 25 + Spring Boot 4.1 | Current LTS/stable releases (verified June 2026). |
| PostgreSQL 18 | Current Postgres major (greenfield). We use DB specifics, not abstract them. |
| Kafka 4.3.1, KRaft | Current stable (verified June 2026). ZooKeeper removed in 4.0. Java 25 supported since 4.2+. |
| Kafka instead of RabbitMQ | Deliberate choice for skill-building / CV; "log, not queue" model. |
| Redpanda Console (Kafka UI) | Clearest view of topics/messages/lag; works with plain Kafka. |
| Mailpit (dev SMTP) | Live successor to the archived MailHog. |
| Valkey (when needed) | BSD-3 — cleaner for OSS than Redis (AGPL/SSPL); drop-in wire-compatible. |
| OpenSearch (when needed) | Apache 2.0 (vs Elastic). |
| Build: Maven | More declarative and simpler for multi-module at our scope. |
| Compose file `stack.yml` | Explicit persistent name instead of the default `docker-compose.yml`. |
| Google Java Style + Spotless + Checkstyle | A named public standard = provenance for code style. |
| Atomic claim = conditional UPDATE | Standard technique under READ COMMITTED (single conditional UPDATE, check affected rows). |
| Bank protocol (domain) | From the Agroprombank "Web-payment" v2.0 technical documentation. |
| OSS license Apache 2.0 | Explicit patent grant, corporate-friendly. |
| `.java` header = `Apache-2.0 + DegDev` | NOT a default/corporate template. A global Claude Code rule mandated a Compo header — rejected as clean-room contamination; the repo-local rule (CLAUDE.md/notes) overrides the global one. First clean-room risk that fired — the documentary layer caught it. |

## TODO (close in recon tickets)
- Exact image pins: Redpanda Console, MinIO, Mailpit (currently :latest in stack.yml).
- Domain package: `io.github.degdev.homeservice.*` vs `md.homeservice.*` — at domain init.
- auth controller opt-in mechanism: feature-flag autoconfig vs explicit bean registration (milestone 02).
