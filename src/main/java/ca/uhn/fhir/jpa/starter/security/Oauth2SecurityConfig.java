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

import de.gematik.isik.mockserver.smart.SmartJwtAutheticationConverter;
import de.gematik.isik.mockserver.smart.SmartTokenIntrospectionController;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configures the Spring Security layer for protecting FHIR endpoints with OAuth2/JWT. It needs to
 * be defined under the HAPI FHIR Package * Namespace, due to SpringBoot configuration scanning.
 */
@ConditionalOnProperty(
    name = {"spring.security.oauth2.enable"},
    havingValue = "true")
@Slf4j
@Configuration
@EnableWebSecurity
public class Oauth2SecurityConfig {

  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
  private String jwkSetUri;

  private final SmartJwtAutheticationConverter smartJwtAutheticationConverter;

  public Oauth2SecurityConfig(final SmartJwtAutheticationConverter smartJwtAutheticationConverter) {
    this.smartJwtAutheticationConverter = smartJwtAutheticationConverter;
  }

  @PostConstruct
  public void init() {
    log.info("Oauth2SecurityConfig loaded");
  }

  /**
   * Provides the {@link JwtDecoder} bean used to validate incoming bearer tokens against the
   * Keycloak JWK set. Only instantiated when OAuth2 security is enabled so that no outbound network
   * call is made during plain (non-secured) runs.
   *
   * <p>Exposing this as a named bean allows tests to replace it with a {@code @MockBean} without
   * touching the rest of the security configuration.
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    log.debug("Creating NimbusJwtDecoder with JWK Set URI: {}", jwkSetUri);
    return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
  }

  /**
   * {@link RestTemplate} used by {@link SmartTokenIntrospectionController} to proxy introspection
   * requests to Keycloak. Only registered when OAuth2 security is enabled.
   */
  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  /**
   * CORS policy for SMART-on-FHIR endpoints.
   *
   * <ul>
   *   <li>Discovery and metadata endpoints: permit any origin (public documents).
   *   <li>FHIR API and token-related endpoints: permit any origin with standard FHIR headers (for a
   *       mock server; restrict to registered origins in production).
   * </ul>
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    // Public discovery / metadata: allow any origin, GET + OPTIONS only.
    CorsConfiguration publicConfig = new CorsConfiguration();
    publicConfig.setAllowedOriginPatterns(List.of("*"));
    publicConfig.setAllowedMethods(List.of("GET", "OPTIONS"));
    publicConfig.setAllowedHeaders(List.of("*"));
    publicConfig.setMaxAge(3600L);

    // FHIR API + token proxy: allow any origin, all FHIR verbs.
    CorsConfiguration fhirConfig = new CorsConfiguration();
    fhirConfig.setAllowedOriginPatterns(List.of("*"));
    fhirConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    fhirConfig.setAllowedHeaders(List.of("*"));
    fhirConfig.setExposedHeaders(List.of("Location", "Content-Location", "ETag"));
    fhirConfig.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/fhir/.well-known/smart-configuration", publicConfig);
    source.registerCorsConfiguration("/fhir/metadata", publicConfig);
    source.registerCorsConfiguration("/fhir/token/introspect", fhirConfig);
    source.registerCorsConfiguration("/fhir/launch", publicConfig);
    source.registerCorsConfiguration("/fhir/**", fhirConfig);
    return source;
  }

  /**
   * Builds the Spring Security filter chain.
   *
   * <p>The {@link JwtDecoder} is supplied via an {@link ObjectProvider} so that this bean can be
   * constructed even when {@code spring.security.oauth2.enable=false} and no decoder bean is
   * registered. When OAuth2 is enabled the provider resolves to the production {@link
   * #jwtDecoder()} bean or – in tests – to a {@code @MockBean} replacement.
   */
  @Bean
  public SecurityFilterChain filterChain(
      final HttpSecurity http, final ObjectProvider<JwtDecoder> jwtDecoderProvider)
      throws Exception {

    JwtDecoder decoder = jwtDecoderProvider.getObject();

    log.info("Initialising SecurityConfig OAuth2 Resource Server");
    log.debug("JWK Set URI: {}", jwkSetUri);

    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/favicon.ico",
                        "/img/**",
                        "/css/**",
                        "/js/**",
                        "/resources/**",
                        "/content/custom/**",
                        "/fhir/metadata",
                        "/fhir/.well-known/smart-configuration",
                        "/fhir/token/introspect",
                        "/fhir/launch")
                    .permitAll()
                    .requestMatchers("/fhir/**")
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt ->
                        jwt.decoder(decoder)
                            .jwtAuthenticationConverter(smartJwtAutheticationConverter)))
        .csrf(AbstractHttpConfigurer::disable);

    log.info("SecurityConfig configured successfully");
    log.debug("Protected paths: /fhir/**");
    log.debug(
        "Public paths: /fhir/metadata, /fhir/.well-known/smart-configuration,"
            + " /fhir/token/introspect, /fhir/launch");

    return http.build();
  }
}
