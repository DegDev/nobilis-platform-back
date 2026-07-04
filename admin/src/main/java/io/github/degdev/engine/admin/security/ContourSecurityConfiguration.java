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
package io.github.degdev.engine.admin.security;

import io.github.degdev.engine.auth.gate.JwtAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Wires the admin contour on top of the auth gate and makes their order deterministic.
 *
 * <p>The gate ({@link JwtAuthenticationFilter}, a mechanism that only reads a token into the
 * request context and never rejects) is registered to run FIRST; the {@link AdminContourFilter}
 * (policy: reject anonymous / non-admin) runs immediately AFTER, so it sees the claims the gate
 * bound. Both go through explicit {@link FilterRegistrationBean}s on purpose: an auto-registered
 * {@code Filter} bean defaults to {@code LOWEST_PRECEDENCE}, and two such filters would tie with no
 * defined order. Referencing the gate bean from a registration bean also suppresses its own default
 * auto-registration (see {@code ServletContextInitializerBeans}), so it is mapped once.
 *
 * <p>Guarded on {@code nobilis.auth.jwt.secret} — the same trigger that mounts the gate ({@code
 * GateAutoConfiguration}) — so a host without a signing secret is simply unsecured rather than
 * failing to start on a missing gate bean. The engine never force-secures a host.
 */
@Configuration
@ConditionalOnProperty(prefix = "nobilis.auth.jwt", name = "secret")
public class ContourSecurityConfiguration {

  /** The gate registration runs 10 slots ahead of the contour so claims are bound first. */
  private static final int GATE_ORDER = Ordered.LOWEST_PRECEDENCE - 10;

  private static final int CONTOUR_ORDER = Ordered.LOWEST_PRECEDENCE;

  @Bean
  public AdminContourFilter adminContourFilter() {
    return new AdminContourFilter();
  }

  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> gateFilterRegistration(
      JwtAuthenticationFilter gateFilter) {
    FilterRegistrationBean<JwtAuthenticationFilter> registration =
        new FilterRegistrationBean<>(gateFilter);
    registration.addUrlPatterns("/*");
    registration.setOrder(GATE_ORDER);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<AdminContourFilter> contourFilterRegistration(
      AdminContourFilter contourFilter) {
    FilterRegistrationBean<AdminContourFilter> registration =
        new FilterRegistrationBean<>(contourFilter);
    registration.addUrlPatterns("/*");
    registration.setOrder(CONTOUR_ORDER);
    return registration;
  }
}
