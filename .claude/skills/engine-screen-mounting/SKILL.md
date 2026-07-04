---
name: engine-screen-mounting
description: Wiring recipe for bringing a new engine admin screen's backend online so it mounts ONLY when its DB store exists and never half-mounts on the stateless host — load before adding a @RestController backed by a JPA service, or any service/controller auto-config for an admin screen. Backend only (Spring Boot auto-configuration).
---

## When this fires

Adding an engine admin screen's backend: an entity+repository exist, a service manages them, a
@RestController exposes them under /admin/api/** — and it must mount only when a database profile
is on.

## Top checklist (full recipe is the SSOT below)

- Service via `@AutoConfiguration(after = HibernateJpaAutoConfiguration)` +
  `@ConditionalOnBean(EntityManagerFactory)` — NOT `@ConditionalOnBean(TheRepository)`.
- `@AutoConfigurationPackage` for the persistence module stays UNCONDITIONAL.
- Controller via a host `@AutoConfiguration` gated `@ConditionalOnBean(TheService)` AND added to the
  host `@ComponentScan` exclude (else it half-mounts and the stateless host fails to boot).
- Lazy `@ElementCollection`/`@ManyToMany` force-initialized inside the service `@Transactional`
  (open-in-view=false).
- Mount present AND absent both tested.

## Full recipe (SSOT — follow it, do not re-derive)

`docs/playbooks/engine-screen-mounting.md`
