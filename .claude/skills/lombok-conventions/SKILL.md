---
name: lombok-conventions
description: Lombok annotation conventions for backend data classes — before creating or editing any entity, DTO, record, @ConfigurationProperties binding, or service, load this to pick the right annotation set (record vs @Getter/@Setter, never @Data/@Value; entity business-key equals; hand-written ctor when it validates/decodes). Backend only (Lombok is JVM).
---

## When this fires

Creating or editing a backend data class, DTO, entity, record, config-properties binding, or
service — before writing the annotations.

## Top checklist (full recipe is the SSOT below)

- Immutable value carrier → Java `record` (never `@Value`/`@Builder`).
- Mutable DTO → `@Getter @Setter` (never `@Data`); prefer a record first.
- `@Entity` → `@Getter`, `@Setter` on mutable fields only, `@NoArgsConstructor(PROTECTED)`,
  business-key-only `@EqualsAndHashCode`. Never `@Data`.
- Service → `@RequiredArgsConstructor` (+`@Slf4j` if it logs); hand-written ctor (no annotation)
  when it validates/decodes/derives.

## Full recipe (SSOT — follow it, do not re-derive)

`docs/playbooks/lombok-conventions.md`
