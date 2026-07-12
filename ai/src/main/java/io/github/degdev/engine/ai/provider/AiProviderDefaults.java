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
package io.github.degdev.engine.ai.provider;

import io.github.degdev.engine.ai.profile.AiProfileException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only access to the AI-provider catalog: the field descriptors a data-driven form renders
 * from, the plain default-value map {@code AiProfileService} merges saved params over, and the
 * default provider for a purpose that has no saved profile yet. Static seed data this milestone —
 * admin-editable catalog CRUD is a later concern, so every method here is a pure read.
 *
 * <p>Not a {@code @Service}: wired as an explicit {@code @Bean} by {@code
 * AiServiceAutoConfiguration}, gated on JPA being active. Lombok {@code @RequiredArgsConstructor}
 * wires the {@code final} repositories.
 */
@RequiredArgsConstructor
public class AiProviderDefaults {

  private final AiProviderFieldRepository fieldRepository;
  private final AiProviderFieldOptionRepository optionRepository;
  private final AiProviderPurposeRepository purposeRepository;
  private final AiProviderRepository providerRepository;

  /**
   * Every purpose the catalog knows about — the admin screen's purpose picker (slice 4).
   *
   * @return the distinct purpose keys, alphabetical
   */
  @Transactional(readOnly = true)
  public List<String> purposes() {
    return purposeRepository.findDistinctPurposes();
  }

  /**
   * The providers offered for a purpose, in display/preference order — the admin screen's provider
   * picker (slice 4).
   *
   * @param purpose the purpose key
   * @return the matching providers, ordered by the purpose link's {@code sortOrder}
   */
  @Transactional(readOnly = true)
  public List<AiProvider> providers(String purpose) {
    return purposeRepository.findByIdPurposeOrderBySortOrder(purpose).stream()
        .map(link -> providerRepository.findById(link.getId().getProviderCode()))
        .flatMap(Optional::stream)
        .toList();
  }

  /**
   * The field descriptors for a {@code (purpose, provider)} pair — what a data-driven admin form
   * renders from.
   *
   * @param purpose the purpose key
   * @param providerCode the provider code
   * @return the matching fields, in display order
   */
  @Transactional(readOnly = true)
  public List<AiFieldDescriptor> fields(String purpose, String providerCode) {
    return fieldRepository
        .findByPurposeAndProviderCodeOrderBySortOrder(purpose, providerCode)
        .stream()
        .map(this::toDescriptor)
        .toList();
  }

  /**
   * The catalog fields for a {@code (purpose, provider)} pair, keyed by their {@code fieldKey} —
   * the lookup {@code AiProfileService} validates submitted params against.
   *
   * @param purpose the purpose key
   * @param providerCode the provider code
   * @return the matching fields, keyed by field key
   */
  @Transactional(readOnly = true)
  public Map<String, AiProviderField> fieldsByKey(String purpose, String providerCode) {
    return fieldRepository
        .findByPurposeAndProviderCodeOrderBySortOrder(purpose, providerCode)
        .stream()
        .collect(
            Collectors.toMap(
                AiProviderField::getFieldKey, f -> f, (a, b) -> b, LinkedHashMap::new));
  }

  /**
   * The plain field-key/default-value map for a {@code (purpose, provider)} pair, excluding {@code
   * SECRET} fields — the base a saved profile's params are merged over. {@code SECRET} values are
   * never part of a plain map; they only exist via {@code AiSecretStore}.
   *
   * @param purpose the purpose key
   * @param providerCode the provider code
   * @return field key to default value, for non-secret fields only
   */
  @Transactional(readOnly = true)
  public Map<String, String> defaultParams(String purpose, String providerCode) {
    return fieldRepository
        .findByPurposeAndProviderCodeOrderBySortOrder(purpose, providerCode)
        .stream()
        .filter(field -> field.getCategory() != AiFieldCategory.SECRET)
        .collect(
            Collectors.toMap(
                AiProviderField::getFieldKey,
                AiProviderField::getDefaultValue,
                (a, b) -> b,
                LinkedHashMap::new));
  }

  /**
   * The default provider for a purpose — the lowest-{@code sortOrder} entry in {@code
   * ai_provider_purpose} — used when a purpose has no saved {@link
   * io.github.degdev.engine.ai.profile.AiProfile} yet.
   *
   * @param purpose the purpose key
   * @return the default provider's code
   * @throws AiProfileException if the purpose has no provider configured in the catalog
   */
  @Transactional(readOnly = true)
  public String defaultProviderCode(String purpose) {
    return purposeRepository.findByIdPurposeOrderBySortOrder(purpose).stream()
        .findFirst()
        .map(link -> link.getId().getProviderCode())
        .orElseThrow(
            () -> new AiProfileException("error.ai-no-default-provider-for-purpose", purpose));
  }

  private AiFieldDescriptor toDescriptor(AiProviderField field) {
    List<String> options =
        (field.getType() == AiFieldType.SELECT || field.getType() == AiFieldType.MULTISELECT)
            ? optionRepository.findByFieldIdOrderBySortOrder(field.getId()).stream()
                .map(AiProviderFieldOption::getValue)
                .toList()
            : List.of();
    return new AiFieldDescriptor(
        field.getFieldKey(),
        field.getCategory(),
        field.getType(),
        field.isEditable(),
        field.getDefaultValue(),
        field.getMinValue(),
        field.getMaxValue(),
        options);
  }
}
