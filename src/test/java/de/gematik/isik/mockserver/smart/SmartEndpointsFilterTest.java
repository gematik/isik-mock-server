package de.gematik.isik.mockserver.smart;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SmartEndpointsFilter}.
 *
 * <p>Verifies that the filter intercepts {@code GET /fhir/.well-known/smart-configuration}, writes
 * the SMART discovery document to the response, and does not continue the filter chain.
 */
class SmartEndpointsFilterTest {

  private SmartEndpointsFilter filter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    SmartConfigProperties properties = new SmartConfigProperties();
    properties.setFhirBaseUrl("http://fhir.example.org/fhir");
    properties.setAuthorizationServerUrl("http://keycloak.example.org/realms/fhir");

    SmartConfigController smartConfigController = new SmartConfigController(properties);
    SmartLaunchController smartLaunchController =
        new SmartLaunchController(properties, new SmartLaunchContextStore());
    SmartTokenIntrospectionController smartTokenIntrospectionController =
        new SmartTokenIntrospectionController(properties, new RestTemplate());
    filter =
        new SmartEndpointsFilter(
            smartConfigController,
            smartLaunchController,
            smartTokenIntrospectionController,
            objectMapper);
  }

  @Nested
  @DisplayName("Interception of well-known path")
  class Interception {

    @Test
    @DisplayName("GET /fhir/.well-known/smart-configuration: returns 200 and writes JSON")
    void handlesGetRequest() throws Exception {
      HttpServletRequest request = mockRequest("GET", SmartEndpointsFilter.WELL_KNOWN_PATH, "");
      StringWriter body = new StringWriter();
      HttpServletResponse response = mockResponse(body);
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(request, response, chain);

      verify(response).setStatus(HttpServletResponse.SC_OK);
      verify(response).setContentType("application/json");
      verify(chain, never()).doFilter(request, response);

      // Response body must be valid JSON containing at least the token_endpoint key.
      @SuppressWarnings("unchecked")
      Map<String, Object> doc = objectMapper.readValue(body.toString(), Map.class);
      assertThat(doc).containsKey("token_endpoint");
      assertThat(doc).containsKey("issuer");
    }

    @Test
    @DisplayName("OPTIONS /fhir/.well-known/smart-configuration: returns 204 (CORS preflight)")
    void handlesOptionsRequest() throws Exception {
      HttpServletRequest request = mockRequest("OPTIONS", SmartEndpointsFilter.WELL_KNOWN_PATH, "");
      StringWriter body = new StringWriter();
      HttpServletResponse response = mockResponse(body);
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(request, response, chain);

      verify(response).setStatus(HttpServletResponse.SC_NO_CONTENT);
      verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("CORS headers are added for well-known requests")
    void addsCorsHeaders() throws Exception {
      HttpServletRequest request = mockRequest("GET", SmartEndpointsFilter.WELL_KNOWN_PATH, "");
      StringWriter body = new StringWriter();
      HttpServletResponse response = mockResponse(body);
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(request, response, chain);

      verify(response).setHeader("Access-Control-Allow-Origin", "*");
    }
  }

  @Nested
  @DisplayName("Pass-through for other paths")
  class PassThrough {

    @Test
    @DisplayName("GET /fhir/Patient: continues the filter chain")
    void passesOtherPaths() throws Exception {
      HttpServletRequest request = mockRequest("GET", "/fhir/Patient", "");
      HttpServletResponse response = mock(HttpServletResponse.class);
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("GET /fhir/metadata: continues the filter chain")
    void passesMetadataPath() throws Exception {
      HttpServletRequest request = mockRequest("GET", "/fhir/metadata", "");
      HttpServletResponse response = mock(HttpServletResponse.class);
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Context path is stripped before comparison")
    void stripsContextPath() throws Exception {
      HttpServletRequest request =
          mockRequest("GET", "/app/fhir/.well-known/smart-configuration", "/app");
      StringWriter body = new StringWriter();
      HttpServletResponse response = mockResponse(body);
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(request, response, chain);

      verify(response).setStatus(HttpServletResponse.SC_OK);
      verify(chain, never()).doFilter(request, response);
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private HttpServletRequest mockRequest(
      final String method, final String uri, final String ctxPath) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn(uri);
    when(req.getContextPath()).thenReturn(ctxPath);
    when(req.getMethod()).thenReturn(method);
    return req;
  }

  private HttpServletResponse mockResponse(final StringWriter body) throws Exception {
    HttpServletResponse resp = mock(HttpServletResponse.class);
    when(resp.getWriter()).thenReturn(new PrintWriter(body));
    return resp;
  }
}
