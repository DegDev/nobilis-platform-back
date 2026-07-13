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
package io.github.degdev.engine.auth.token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

/**
 * Issues and validates compact JWTs signed with HMAC-SHA256 (the {@code HS256} algorithm of RFC
 * 7519 / 7515), implemented directly on the JCA — matching the engine's hand-rolled-primitive house
 * style (see {@code common.crypto}) and keeping the dependency surface minimal.
 *
 * <p>A token is {@code base64url(header) + "." + base64url(payload) + "." + base64url(signature)}.
 * The header is fixed ({@code {"alg":"HS256","typ":"JWT"}}); the payload carries {@code sub},
 * {@code roles}, {@code realms}, {@code permissions}, {@code iat}, {@code exp}, and {@code
 * loginAt}. Validation recomputes the signature and compares it in constant time ({@link
 * MessageDigest#isEqual}), then rejects an expired {@code exp}. The signing key is a Base64-encoded
 * secret of at least 256 bits; a missing or too-short key fails fast at construction so a
 * misconfigured deployment never issues unsignable tokens.
 */
public final class JwtService {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
  private static final int MIN_KEY_BYTES = 32;
  private static final int TOKEN_SEGMENTS = 3;

  private final byte[] key;
  private final Duration ttl;
  private final Clock clock;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Builds the service from the configured signing secret, failing fast on a missing, non-Base64,
   * or too-short key.
   *
   * @param properties holds the Base64-encoded HMAC key and token TTL
   * @param clock the time source for {@code iat}/{@code exp}; inject {@link Clock#systemUTC()} in
   *     production and a fixed clock in tests
   * @throws IllegalStateException if the secret is absent, not valid Base64, or shorter than 256
   *     bits
   */
  public JwtService(JwtProperties properties, Clock clock) {
    if (properties == null || !StringUtils.hasText(properties.secret())) {
      throw new IllegalStateException(
          "Missing required property 'nobilis.auth.jwt.secret'. Supply a Base64-encoded key of at"
              + " least 256 bits via the environment (never commit it).");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(properties.secret().trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("'nobilis.auth.jwt.secret' is not valid Base64", e);
    }
    if (decoded.length < MIN_KEY_BYTES) {
      throw new IllegalStateException(
          "'nobilis.auth.jwt.secret' must decode to at least "
              + MIN_KEY_BYTES
              + " bytes (256 bits) for HS256; got "
              + decoded.length);
    }
    this.key = decoded;
    this.ttl = properties.ttl();
    this.clock = clock;
  }

  /**
   * Issues a "thin" token carrying only the subject and roles; realms and permissions are empty.
   * Kept for callers that do not need the thick claims.
   *
   * @param subject the identity the token is about (the {@code sub} claim)
   * @param roles authorities to embed (the {@code roles} claim); may be empty, never {@code null}
   * @return the compact {@code header.payload.signature} token
   */
  public String issue(String subject, List<String> roles) {
    return issue(subject, roles, List.of(), List.of());
  }

  /**
   * Issues a "thick" signed token, valid from now until {@code now + ttl}, embedding the realms and
   * effective permissions alongside the subject and roles so a gate can authorize without a
   * database round-trip. The {@code loginAt} claim is set to now — use this overload only for a
   * real, credential-verified login; a silent re-mint must call {@link #issue(String, List, List,
   * List, Instant)} instead so it carries the original login instant forward unchanged.
   *
   * @param subject the identity the token is about (the {@code sub} claim)
   * @param roles role codes to embed (the {@code roles} claim); may be empty, never {@code null}
   * @param realms realm names to embed (the {@code realms} claim); may be empty, never {@code null}
   * @param permissions permission strings to embed (the {@code permissions} claim); may be empty,
   *     never {@code null}
   * @return the compact {@code header.payload.signature} token
   */
  public String issue(
      String subject, List<String> roles, List<String> realms, List<String> permissions) {
    Instant now = clock.instant();
    return issue(subject, roles, realms, permissions, now);
  }

  /**
   * Issues a "thick" signed token, valid from now until {@code now + ttl}, with an explicit {@code
   * loginAt} claim. This is the primitive a silent token re-mint uses: it carries the {@code
   * loginAt} of the token being re-minted forward UNCHANGED, so a caller cannot extend their
   * effective session past the staleness cap by chaining re-mints.
   *
   * @param subject the identity the token is about (the {@code sub} claim)
   * @param roles role codes to embed (the {@code roles} claim); may be empty, never {@code null}
   * @param realms realm names to embed (the {@code realms} claim); may be empty, never {@code null}
   * @param permissions permission strings to embed (the {@code permissions} claim); may be empty,
   *     never {@code null}
   * @param loginAt the {@code loginAt} claim to embed — the original real-login instant
   * @return the compact {@code header.payload.signature} token
   */
  public String issue(
      String subject,
      List<String> roles,
      List<String> realms,
      List<String> permissions,
      Instant loginAt) {
    Instant now = clock.instant();
    Instant expiry = now.plus(ttl);

    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", subject);
    claims.put("roles", roles);
    claims.put("realms", realms);
    claims.put("permissions", permissions);
    claims.put("iat", now.getEpochSecond());
    claims.put("exp", expiry.getEpochSecond());
    claims.put("loginAt", loginAt.getEpochSecond());

    String headerSegment = base64Url(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
    String payloadSegment = base64Url(toJson(claims).getBytes(StandardCharsets.UTF_8));
    String signingInput = headerSegment + "." + payloadSegment;
    String signatureSegment = base64Url(sign(signingInput));
    return signingInput + "." + signatureSegment;
  }

  /**
   * Verifies a token's signature and expiry and returns its claims.
   *
   * @param token a compact token previously issued by {@link #issue(String, List)}
   * @return the verified claims
   * @throws JwtException if the token is malformed, its signature does not verify, or it has
   *     expired
   */
  public AuthClaims validate(String token) {
    JsonNode payload = verifyAndParse(token);
    long exp = payload.path("exp").asLong();
    if (clock.instant().getEpochSecond() >= exp) {
      throw new JwtException("token has expired");
    }
    return toClaims(payload, exp);
  }

  /**
   * Verifies a token's signature and returns its claims, tolerating an expiry up to {@code grace}
   * in the past. This exists ONLY for the token re-mint endpoint's reactive case (a request that
   * raced an expiring token and got a 401) — no other caller should use it. Everywhere else, an
   * expired token must be rejected outright by {@link #validate(String)}.
   *
   * @param token a compact token previously issued by one of the {@code issue} overloads
   * @param grace how far past {@code exp} the token is still accepted
   * @return the verified claims
   * @throws JwtException if the token is malformed, its signature does not verify, or it expired
   *     more than {@code grace} ago
   */
  public AuthClaims validateForRemint(String token, Duration grace) {
    JsonNode payload = verifyAndParse(token);
    long exp = payload.path("exp").asLong();
    long graceDeadline = exp + grace.toSeconds();
    if (clock.instant().getEpochSecond() >= graceDeadline) {
      throw new JwtException("token has expired beyond the remint grace window");
    }
    return toClaims(payload, exp);
  }

  private JsonNode verifyAndParse(String token) {
    if (token == null) {
      throw new JwtException("token must not be null");
    }
    String[] parts = token.split("\\.");
    if (parts.length != TOKEN_SEGMENTS) {
      throw new JwtException("malformed token: expected 3 segments");
    }
    String signingInput = parts[0] + "." + parts[1];
    byte[] expected = sign(signingInput);
    byte[] actual;
    try {
      actual = Base64.getUrlDecoder().decode(parts[2]);
    } catch (IllegalArgumentException e) {
      throw new JwtException("malformed token: signature is not valid base64url", e);
    }
    if (!MessageDigest.isEqual(expected, actual)) {
      throw new JwtException("token signature did not verify");
    }
    return parsePayload(parts[1]);
  }

  private static AuthClaims toClaims(JsonNode payload, long exp) {
    long iat = payload.path("iat").asLong();
    long loginAt = payload.hasNonNull("loginAt") ? payload.path("loginAt").asLong() : iat;
    return new AuthClaims(
        payload.path("sub").asText(null),
        readStringList(payload, "roles"),
        readStringList(payload, "realms"),
        readStringList(payload, "permissions"),
        Instant.ofEpochSecond(iat),
        Instant.ofEpochSecond(exp),
        Instant.ofEpochSecond(loginAt));
  }

  private JsonNode parsePayload(String payloadSegment) {
    try {
      byte[] json = Base64.getUrlDecoder().decode(payloadSegment);
      return mapper.readTree(json);
    } catch (IllegalArgumentException | java.io.IOException e) {
      throw new JwtException("malformed token: payload is not valid base64url JSON", e);
    }
  }

  private static List<String> readStringList(JsonNode payload, String field) {
    List<String> values = new ArrayList<>();
    JsonNode node = payload.get(field);
    if (node != null && node.isArray()) {
      node.forEach(element -> values.add(element.asText()));
    }
    return List.copyOf(values);
  }

  private String toJson(Map<String, Object> claims) {
    try {
      return mapper.writeValueAsString(claims);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new JwtException("failed to serialise token claims", e);
    }
  }

  private byte[] sign(String signingInput) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
      return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new JwtException("HMAC-SHA256 signing failed", e);
    }
  }

  private static String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
