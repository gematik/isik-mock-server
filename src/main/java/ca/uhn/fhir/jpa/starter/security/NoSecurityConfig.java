package ca.uhn.fhir.jpa.starter.security;

/*-
 * #%L
 * isik-mock-server
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes
 * by gematik, find details in the "Readme" file.
 * #L%
 */

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Disables the Security Configuration (by default), preventing the redirect of requests to a /login
 * endpoint, if the property is disabled. It needs to be defined under the HAPI FHIR Package
 * Namespace, due to SpringBoot configuration scanning.
 */
@ConditionalOnProperty(
    name = "spring.security.oauth2.enable",
    havingValue = "false",
    matchIfMissing = true)
@Slf4j
@Configuration
@EnableWebSecurity
public class NoSecurityConfig {

  @PostConstruct
  public void init() {
    log.info("NoSecurityConfig loaded");
  }

  @Bean
  public SecurityFilterChain noSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .securityContext(AbstractHttpConfigurer::disable)
        .sessionManagement(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
