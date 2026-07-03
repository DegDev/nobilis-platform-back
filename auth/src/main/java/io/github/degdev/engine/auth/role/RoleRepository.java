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
package io.github.degdev.engine.auth.role;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link Role} aggregates. */
public interface RoleRepository extends JpaRepository<Role, Long> {

  /**
   * Resolves a role by its business key.
   *
   * @param code the unique role code
   * @return the matching role, or empty if none
   */
  Optional<Role> findByCode(String code);

  /**
   * Counts the accounts currently assigned the given role — used to guard deletion against the
   * {@code account_role} foreign key ({@code NO ACTION}). The JPQL walks the join from the {@code
   * Account} side (which owns the {@code @ManyToMany}), so this needs no compile-time dependency on
   * the account package.
   *
   * @param role the role to check for references
   * @return the number of accounts holding the role
   */
  @Query("select count(a) from Account a join a.roles r where r = :role")
  long countAssignedAccounts(@Param("role") Role role);
}
