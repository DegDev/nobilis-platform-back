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
package io.github.degdev.engine.admin.ai;

import io.github.degdev.engine.admin.api.NobilisAdminController;
import io.github.degdev.engine.ai.llm.AiHealthCheckResult;
import io.github.degdev.engine.ai.llm.OllamaHealthCheckService;
import io.github.degdev.engine.ai.profile.AiProfileException;
import io.github.degdev.engine.ai.profile.AiProfileService;
import io.github.degdev.engine.ai.profile.AiSecretStore;
import io.github.degdev.engine.ai.profile.ResolvedAiProfile;
import io.github.degdev.engine.ai.provider.AiFieldCategory;
import io.github.degdev.engine.ai.provider.AiFieldDescriptor;
import io.github.degdev.engine.ai.provider.AiProviderDefaults;
import io.github.degdev.engine.auth.role.EnginePermissions;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Thin REST layer over the AI-profile services (slice 2/3): no business logic here — every handler
 * delegates to {@link AiProviderDefaults} (catalog reads) or {@link AiProfileService}
 * (resolve/save/validate), and the health-check handler delegates to {@link
 * OllamaHealthCheckService}.
 *
 * <p><b>Secret handling.</b> {@link ResolvedAiProfile#params()} already excludes {@code
 * SECRET}-category fields (slice 2), so nothing here ever has a secret's plaintext in hand except
 * at the one write path ({@link #saveProfile}, which forwards straight to {@link
 * AiSecretStore#store}). Reads report only whether a secret field is SET, never its value — mirrors
 * {@code SettingDto}'s masking, adapted to a per-field map.
 *
 * <p><b>Optional collaborators.</b> {@link AiSecretStore} (crypto-gated) and {@link
 * OllamaHealthCheckService} (base-url-gated) mount independently of {@link AiProfileService}
 * (JPA-gated) — see {@code AiServiceAutoConfiguration}/{@code AiLlmAutoConfiguration}. Both are
 * injected as {@link ObjectProvider} so a host with JPA but no crypto/no configured Ollama endpoint
 * still gets the read/save endpoints; only the secret-write and health-check paths degrade
 * gracefully (a clear error/{@code ok=false}, never a missing-bean failure).
 *
 * <p><b>Mounted only with a profile store.</b> Like every other engine screen, this is a
 * {@code @RestController} EXCLUDED from the host's component scan (see {@code AdminApplication})
 * and instead registered as a {@code @Bean} by {@code AiAdminAutoConfiguration}, gated on {@link
 * AiProfileService} existing.
 */
@NobilisAdminController(permission = EnginePermissions.AI_MANAGE)
@RequestMapping("${nobilis.api.v1.url:/api}/admin/ai")
public class AiProfileController {

  private final AiProviderDefaults providerDefaults;
  private final AiProfileService profileService;
  private final ObjectProvider<AiSecretStore> secretStoreProvider;
  private final ObjectProvider<OllamaHealthCheckService> healthCheckProvider;

  /**
   * Creates the controller.
   *
   * @param providerDefaults read-only catalog access
   * @param profileService profile resolve/save
   * @param secretStoreProvider the secret store, if crypto is configured
   * @param healthCheckProvider the health-check probe, if an LLM base URL is configured
   */
  public AiProfileController(
      AiProviderDefaults providerDefaults,
      AiProfileService profileService,
      ObjectProvider<AiSecretStore> secretStoreProvider,
      ObjectProvider<OllamaHealthCheckService> healthCheckProvider) {
    this.providerDefaults = providerDefaults;
    this.profileService = profileService;
    this.secretStoreProvider = secretStoreProvider;
    this.healthCheckProvider = healthCheckProvider;
  }

  /**
   * Lists every purpose the catalog knows about.
   *
   * @return the distinct purpose keys
   */
  @GetMapping("/purposes")
  public List<String> purposes() {
    return providerDefaults.purposes();
  }

  /**
   * Lists the providers offered for a purpose.
   *
   * @param purpose the purpose key
   * @return the matching providers
   */
  @GetMapping("/providers")
  public List<AiProviderDto> providers(@RequestParam String purpose) {
    return providerDefaults.providers(purpose).stream().map(AiProviderDto::from).toList();
  }

  /**
   * The field descriptor a data-driven form renders from.
   *
   * @param purpose the purpose key
   * @param provider the provider code
   * @return the catalog fields for this pair, in display order
   */
  @GetMapping("/descriptor")
  public List<AiFieldDescriptor> descriptor(
      @RequestParam String purpose, @RequestParam String provider) {
    return providerDefaults.fields(purpose, provider);
  }

  /**
   * Reads the currently resolved profile for a purpose. Secret fields are masked (see class docs).
   *
   * @param purpose the purpose key
   * @return the resolved, masked profile
   */
  @GetMapping("/profile")
  public AiProfileDto getProfile(@RequestParam String purpose) {
    return toDto(profileService.resolve(purpose));
  }

  /**
   * Saves which provider serves a purpose, its operational params, and any submitted secrets.
   *
   * @param request the validated save request
   * @return the resolved, masked profile after saving
   * @throws AiProfileException if the provider/field/value fails catalog validation, or secrets are
   *     submitted with no secret store configured
   */
  @PostMapping("/profile")
  public AiProfileDto saveProfile(@Valid @RequestBody AiProfileSaveRequest request) {
    Map<String, String> params = request.params() == null ? Map.of() : request.params();
    ResolvedAiProfile resolved = profileService.save(request.purpose(), request.provider(), params);

    Map<String, String> secrets = request.secrets() == null ? Map.of() : request.secrets();
    if (!secrets.isEmpty()) {
      AiSecretStore secretStore = secretStoreProvider.getIfAvailable();
      if (secretStore == null) {
        throw new AiProfileException("error.ai-secrets-not-configured");
      }
      secrets.forEach(
          (fieldKey, value) -> {
            if (value != null && !value.isBlank()) {
              secretStore.store(secretRef(request.purpose(), request.provider(), fieldKey), value);
            }
          });
    }
    return toDto(resolved);
  }

  /**
   * Runs a health-check against the purpose's currently resolved provider/model.
   *
   * @param request the validated health-check request
   * @return the check result — {@code ok=false} with a message on any failure (unreachable, model
   *     missing, not configured), NEVER a thrown exception/500
   */
  @PostMapping("/health-check")
  public AiHealthCheckResult healthCheck(@Valid @RequestBody AiHealthCheckRequest request) {
    ResolvedAiProfile resolved = profileService.resolve(request.purpose());
    OllamaHealthCheckService healthCheck = healthCheckProvider.getIfAvailable();
    if (healthCheck == null) {
      return new AiHealthCheckResult(
          false, "LLM client is not configured (nobilis.ai.base-url is not set)");
    }
    return healthCheck.check(resolved.params().get("model"));
  }

  private AiProfileDto toDto(ResolvedAiProfile resolved) {
    List<AiFieldDescriptor> descriptor =
        providerDefaults.fields(resolved.purpose(), resolved.providerCode());
    AiSecretStore secretStore = secretStoreProvider.getIfAvailable();
    Map<String, Boolean> secretsSet =
        descriptor.stream()
            .filter(field -> field.category() == AiFieldCategory.SECRET)
            .collect(
                Collectors.toMap(
                    AiFieldDescriptor::fieldKey,
                    field ->
                        secretStore != null
                            && secretStore.isSet(
                                secretRef(
                                    resolved.purpose(), resolved.providerCode(), field.fieldKey())),
                    (a, b) -> b,
                    LinkedHashMap::new));
    return new AiProfileDto(
        resolved.purpose(), resolved.providerCode(), resolved.params(), secretsSet);
  }

  private static String secretRef(String purpose, String providerCode, String fieldKey) {
    return purpose + "." + providerCode + "." + fieldKey;
  }
}
