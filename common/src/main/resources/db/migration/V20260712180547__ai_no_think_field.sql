-- nobilis-platform :: ai — think-model suppression: seed the Ollama "no-think" catalog field
-- (milestone 06-ai-slice, live finding on slice 3).
--
-- qwen3:8b (and other think-models) leak <think> reasoning into message.content unless Ollama's
-- native top-level "think": false is sent on /api/chat (confirmed live; the old "/no_think" text-
-- prefix trick does NOT work on this Ollama version — dead approach). Default true: a leaking
-- <think> block in the reply is the worse product default; an admin can flip this off per profile
-- to allow reasoning. Data-only change (no schema change) — appended as a new migration rather
-- than editing the already-applied V20260712170028.

insert into ai_provider_field (
    created_at, updated_at, purpose, provider_code, field_key, category, type, editable,
    default_value, min_value, max_value, sort_order
)
values
    (now(), now(), 'default', 'ollama', 'no-think', 'OPERATIONAL', 'BOOLEAN', true,
     'true', null, null, 5)
on conflict (purpose, provider_code, field_key) do nothing;
