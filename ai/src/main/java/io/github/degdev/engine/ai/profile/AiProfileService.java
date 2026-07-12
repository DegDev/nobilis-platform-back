/*
 * Copyright (c) 2026 Dmitri Puscas (DegDev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.degdev.engine.ai.profile;

import io.github.degdev.engine.ai.provider.AiFieldCategory;
import io.github.degdev.engine.ai.provider.AiFieldType;
import io.github.degdev.engine.ai.provider.AiProviderDefaults;
import io.github.degdev.engine.ai.provider.AiProviderField;
import io.github.degdev.engine.ai.provider.AiProviderRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves "which provider serves this purpose, with which params" and persists admin-saved
 * overrides. The mechanism works before any admin ever opens the form: {@link #resolve(String)}
 * with no saved {@link AiProfile} falls back to the catalog's default provider and pure catalog
 * defaults (see {@link AiProviderDefaults}).
 *
 * <p>{@link #save} validates against the catalog — an unknown field key or a value outside the
 * field's {@code minValue}/{@code maxValue} is rejected, never silently dropped or clamped — and
 * upserts the one {@link AiProfile} per purpose plus a full delete-then-insert of its {@link
 * AiProfileParam} rows, mirroring the pattern source's save flow (a save always replaces the
 * complete param set, so a removed field truly disappears rather than lingering as a stale row).
 * {@code SECRET}-category fields are rejected from {@code params} entirely — their plaintext only
 * ever exists via {@link AiSecretStore}'s explicit read, never inside a plain value map that could
 * be logged or serialized.
 *
 * <p>Not a {@code @Service}: wired as an explicit {@code @Bean} by {@code
 * AiServiceAutoConfiguration}, gated on JPA being active. Lombok {@code @RequiredArgsConstructor}
 * wires the {@code final} collaborators; {@code @Slf4j} provides {@code log}.
 */
@Slf4j
@RequiredArgsConstructor
public class AiProfileService {

  private final AiProfileRepository profileRepository;
  private final AiProfileParamRepository paramRepository;
  private final AiProviderRepository providerRepository;
  private final AiProviderDefaults providerDefaults;

  /**
   * Resolves the active provider and effective params for a purpose. With no saved profile, falls
   * back to the purpose's catalog default provider and pure catalog defaults — the default path
   * that proves the mechanism works before any admin-saved override exists.
   *
   * @param purpose the purpose key
   * @return the resolved provider and effective (catalog-default-merged-with-saved) params
   * @throws AiProfileException if the purpose has no saved profile and no default provider is
   *     configured in the catalog
   */
  @Transactional(readOnly = true)
  public ResolvedAiProfile resolve(String purpose) {
    var saved = profileRepository.findByPurpose(purpose);
    String providerCode =
        saved
            .map(AiProfile::getProviderCode)
            .orElseGet(() -> providerDefaults.defaultProviderCode(purpose));

    Map<String, String> effective =
        new LinkedHashMap<>(providerDefaults.defaultParams(purpose, providerCode));
    saved.ifPresent(
        profile ->
            paramRepository
                .findByIdProfileId(profile.getId())
                .forEach(param -> effective.put(param.getId().getFieldKey(), param.getValue())));

    return new ResolvedAiProfile(purpose, providerCode, effective);
  }

  /**
   * Saves which provider serves a purpose and its operational param values, validating every
   * submitted value against the catalog first. Upserts the one {@link AiProfile} per purpose and
   * fully replaces its {@link AiProfileParam} rows.
   *
   * @param purpose the purpose key
   * @param providerCode the chosen provider's code
   * @param params the submitted field-key/value pairs (operational and infra fields only — never
   *     secrets, see {@link AiSecretStore})
   * @return the resolved profile after saving
   * @throws AiProfileException if {@code providerCode} is unknown, a param key is not in the
   *     catalog for this {@code (purpose, providerCode)}, a param targets a {@code SECRET} field,
   *     or a {@code NUMBER} value is unparsable or out of the field's bounds
   */
  @Transactional
  public ResolvedAiProfile save(String purpose, String providerCode, Map<String, String> params) {
    if (!providerRepository.existsById(providerCode)) {
      throw new AiProfileException("error.unknown-ai-provider", providerCode);
    }

    Map<String, AiProviderField> catalog = providerDefaults.fieldsByKey(purpose, providerCode);
    params.forEach((fieldKey, value) -> validate(purpose, providerCode, catalog, fieldKey, value));

    AiProfile profile =
        profileRepository
            .findByPurpose(purpose)
            .map(
                existing -> {
                  existing.setProviderCode(providerCode);
                  return existing;
                })
            .orElseGet(() -> new AiProfile(purpose, null, providerCode));
    profile = profileRepository.save(profile);

    paramRepository.deleteByIdProfileId(profile.getId());
    Long profileId = profile.getId();
    params.forEach(
        (fieldKey, value) ->
            paramRepository.save(
                new AiProfileParam(new AiProfileParamId(profileId, fieldKey), value)));

    log.debug("Saved AI profile for purpose '{}' (provider '{}')", purpose, providerCode);
    return resolve(purpose);
  }

  private static void validate(
      String purpose,
      String providerCode,
      Map<String, AiProviderField> catalog,
      String fieldKey,
      String value) {
    AiProviderField field = catalog.get(fieldKey);
    if (field == null) {
      throw new AiProfileException(
          "error.unknown-ai-provider-field", fieldKey, purpose, providerCode);
    }
    if (field.getCategory() == AiFieldCategory.SECRET) {
      throw new AiProfileException("error.ai-provider-field-is-secret", fieldKey);
    }
    if (field.getType() == AiFieldType.NUMBER) {
      validateNumber(field, value);
    }
  }

  private static void validateNumber(AiProviderField field, String value) {
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(value);
    } catch (NumberFormatException e) {
      throw new AiProfileException(
          "error.ai-provider-field-not-numeric", field.getFieldKey(), value);
    }
    BigDecimal min = field.getMinValue();
    BigDecimal max = field.getMaxValue();
    if ((min != null && parsed.compareTo(min) < 0) || (max != null && parsed.compareTo(max) > 0)) {
      throw new AiProfileException(
          "error.ai-provider-field-out-of-bounds", field.getFieldKey(), value, min, max);
    }
  }
}
