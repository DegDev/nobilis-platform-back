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
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The identity root: one person/system the engine knows, independent of how they authenticate. An
 * account carries a lifecycle {@link AccountStatus} and a set of {@link Realm}s (the coarse
 * admin/client gate). How it proves identity lives in {@link AccountIdentity} (one row per
 * provider); roles and permissions arrive in a later pass.
 *
 * <p>Realms are modelled as an {@code @ElementCollection} rather than a separate entity: they are a
 * value collection owned by the account, with no identity or audit of their own. The mapping yields
 * the {@code account_realm} join table with a composite {@code (account_id, realm)} key and no
 * surrogate id.
 *
 * <p>Lombok: {@code @Getter} on the type; {@code @Setter} only on {@code status}. Equality is left
 * as JVM identity — an account has no natural business key (identity is proven through {@link
 * AccountIdentity}), and the convention forbids equality on the generated id.
 * {@code @NoArgsConstructor(PROTECTED)} satisfies Hibernate without widening the public API.
 */
@Getter
@Entity
@Table(name = "account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private AccountStatus status;

  @ElementCollection
  @CollectionTable(name = "account_realm", joinColumns = @JoinColumn(name = "account_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "realm", nullable = false, length = 16)
  private Set<Realm> realms = new LinkedHashSet<>();

  /**
   * Creates a new account in the given status with no realms yet.
   *
   * @param status the initial lifecycle status
   */
  public Account(AccountStatus status) {
    this.status = status;
  }

  /**
   * Grants a realm to this account (idempotent).
   *
   * @param realm the realm to add
   */
  public void addRealm(Realm realm) {
    this.realms.add(realm);
  }

  /** {@return an unmodifiable view of this account's realms} */
  public Set<Realm> getRealms() {
    return Set.copyOf(realms);
  }
}
