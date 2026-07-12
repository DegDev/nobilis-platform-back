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

## Multi-agent (Codex)

This repo is built with Claude Code, but the same instructions can drive OpenAI
Codex CLI for A/B runs. Codex reads `CLAUDE.md` as its instruction file via the
`project_doc_fallback_filenames` fallback (no `AGENTS.md` needed — one SSOT).
The mapping of the full harness onto Codex is in
[`docs/process/codex-harness-parity.md`](docs/process/codex-harness-parity.md).

Global Codex config lives at `~/.codex/config.toml`:

```toml
model = "gpt-5.6-sol"
model_reasoning_effort = "medium"
project_doc_fallback_filenames = ["CLAUDE.md"]

[projects."/path/to/www/nobilis-platform-front"]
trust_level = "trusted"

[projects."/path/to/www/nobilis-platform-back"]
trust_level = "trusted"

[mcp_servers.context7]
url = "https://mcp.context7.com/mcp"

[mcp_servers.jetbrains]
url = "http://127.0.0.1:64342/stream"
```

## License

Apache-2.0 © 2026 Dmitri Puscas ([@DegDev](https://github.com/DegDev))
