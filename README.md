# nobilis-platform

A universal, modular web engine — Java 25 / Spring Boot 4.1 backend.
Built AI-augmented. The public core of a clone-and-extend platform; domain
products are built on top of it.

> 🚧 **Early development.** The module skeleton and dev infrastructure are in
> place; feature implementation is in progress. The architecture and roadmap
> below describe the intended design, not yet fully built.

## Modules

One-directional dependencies: `common ← {ai, auth} ← {app, admin, integration}`

| Module        | Type             | Purpose                                           |
|---------------|------------------|---------------------------------------------------|
| `common`      | library          | entities, repositories, settings + crypto, i18n   |
| `ai`          | library (opt-in) | provider-agnostic LLM adapters                    |
| `auth`        | library          | tokens, identity providers (email, Telegram, SMS) |
| `app`         | runnable (HTTP)  | portal shell + domain extension points            |
| `admin`       | runnable (HTTP)  | CRUD framework + engine screens                   |
| `integration` | worker (no HTTP) | Kafka bus, notifications, transports, scheduler   |

## Stack

Java 25 · Spring Boot 4.1 · PostgreSQL 18 · Kafka 4.3.1 (KRaft) · MinIO · Ollama

## Development

```bash
docker compose -f stack.yml up -d   # Postgres, Kafka, MinIO, Mailpit, Kafka console
mvn verify                          # build all modules
```

JDK is pinned per-project via SDKMAN (`.sdkmanrc`, Java 25). Run `sdk env` in the
repo root if auto-switching is not enabled.

## License

Apache-2.0 © 2026 Dmitri Puscas ([@DegDev](https://github.com/DegDev))
