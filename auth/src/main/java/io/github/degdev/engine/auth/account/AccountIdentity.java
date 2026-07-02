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
package io.github.degdev.engine.auth.account;

import io.github.degdev.engine.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One way an {@link Account} proves who it is, scoped to a single {@link ProviderType}. The pair
 * {@code (providerType, externalId)} is globally unique — it is the natural key by which a login
 * resolves back to an account (e.g. a Telegram user id, or an email address).
 *
 * <p>{@code secretHash} is nullable on purpose: password-style providers (email/SMS) store a hash
 * here, but Telegram carries no secret of its own — the provider vouches for the identity — so its
 * rows leave the column null.
 *
 * <p>Lombok: {@code @Getter} on the type; {@code @Setter} only on {@code secretHash}. Equality is
 * by the natural key alone ({@code onlyExplicitlyIncluded}, {@code callSuper = false}) — never the
 * generated id and never the {@code account} association — so it is stable across the lifecycle and
 * never triggers lazy loading. {@code @NoArgsConstructor(PROTECTED)} satisfies Hibernate.
 */
@Getter
@Entity
@Table(name = "account_identity")
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountIdentity extends BaseEntity {

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @EqualsAndHashCode.Include
  @Enumerated(EnumType.STRING)
  @Column(name = "provider_type", nullable = false, length = 16)
  private ProviderType providerType;

  @EqualsAndHashCode.Include
  @Column(name = "external_id", nullable = false, length = 255)
  private String externalId;

  @Setter
  @Column(name = "secret_hash", length = 255)
  private String secretHash;

  /**
   * Creates an identity binding an account to a provider-scoped external id.
   *
   * @param account the owning account
   * @param providerType the provider this identity authenticates through
   * @param externalId the provider-scoped identifier (unique per provider)
   * @param secretHash the stored secret hash, or {@code null} for secretless providers (Telegram)
   */
  public AccountIdentity(
      Account account, ProviderType providerType, String externalId, String secretHash) {
    this.account = account;
    this.providerType = providerType;
    this.externalId = externalId;
    this.secretHash = secretHash;
  }
}
