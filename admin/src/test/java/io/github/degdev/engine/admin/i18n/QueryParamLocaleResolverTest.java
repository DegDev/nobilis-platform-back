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
package io.github.degdev.engine.admin.i18n;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.degdev.engine.common.i18n.I18nAutoConfiguration;
import io.github.degdev.engine.common.i18n.LocaleResolver;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CharacterEncodingFilter;

class QueryParamLocaleResolverTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MessageSource messageSource = new I18nAutoConfiguration().messageSource();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProbeController(messageSource))
            .setLocaleResolver(new QueryParamLocaleResolver(new LocaleResolver()))
            .addFilters(new CharacterEncodingFilter(StandardCharsets.UTF_8.name(), true))
            .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
            .build();
  }

  @Test
  void queryParameterEstablishesTheRequestLocale() throws Exception {
    mockMvc
        .perform(get("/probe").queryParam("locale", "ru"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-8"))
        .andExpect(content().string("У вас нет разрешения на выполнение этого действия"));
  }

  @Test
  void absentAndUnsupportedLocalesFallBackToEnglish() throws Exception {
    mockMvc
        .perform(get("/probe").queryParam("locale", "unsupported"))
        .andExpect(status().isOk())
        .andExpect(content().string("You do not have permission to perform this action"));
  }

  @RestController
  private static final class ProbeController {

    private final MessageSource messageSource;

    private ProbeController(MessageSource messageSource) {
      this.messageSource = messageSource;
    }

    @GetMapping(value = "/probe", produces = MediaType.TEXT_PLAIN_VALUE)
    String message() {
      return messageSource.getMessage("error.forbidden", null, LocaleContextHolder.getLocale());
    }
  }
}
