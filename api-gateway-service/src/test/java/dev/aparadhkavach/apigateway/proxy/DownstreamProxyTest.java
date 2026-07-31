package dev.aparadhkavach.apigateway.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.aparadhkavach.commons.header.HeaderConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class DownstreamProxyTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private DownstreamProxy proxy;

  @BeforeEach
  void setUp() {
    proxy = new DownstreamProxy(RestClient.create());
  }

  @Test
  void forwardsGetPathQueryAndHeaders() throws Exception {
    byte[] downstreamBody = "{\"accusedId\":\"ACC-00124\"}".getBytes(StandardCharsets.UTF_8);
    wireMock.stubFor(
        get(urlEqualTo("/v1/accusedPersons/ACC-00124:riskProfile?x=1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(downstreamBody)));

    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getMethod()).thenReturn("GET");
    Mockito.when(request.getRequestURI()).thenReturn("/v1/accusedPersons/ACC-00124:riskProfile");
    Mockito.when(request.getQueryString()).thenReturn("x=1");
    Mockito.when(request.getInputStream())
        .thenReturn(new DelegatingServletInputStream(new byte[0]));
    Vector<String> headerNames = new Vector<>();
    headerNames.add("Authorization");
    headerNames.add("Host");
    Mockito.when(request.getHeaderNames()).thenReturn(headerNames.elements());
    Mockito.when(request.getHeaders("Authorization"))
        .thenReturn(Collections.enumeration(Collections.singletonList("Bearer test-token")));
    Mockito.when(request.getHeaders("Host"))
        .thenReturn(Collections.enumeration(Collections.singletonList("gateway.local")));

    ResponseEntity<byte[]> response = proxy.forward(wireMock.baseUrl(), request);

    assertEquals(200, response.getStatusCode().value());
    assertArrayEquals(downstreamBody, response.getBody());
    assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));

    wireMock.verify(
        getRequestedFor(urlEqualTo("/v1/accusedPersons/ACC-00124:riskProfile?x=1"))
            .withHeader("Authorization", equalTo("Bearer test-token")));
  }

  @Test
  void forwardsCorrelationIdHeader() throws Exception {
    wireMock.stubFor(
        get(urlEqualTo("/v1/analytics/hotspots"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getMethod()).thenReturn("GET");
    Mockito.when(request.getRequestURI()).thenReturn("/v1/analytics/hotspots");
    Mockito.when(request.getQueryString()).thenReturn(null);
    Mockito.when(request.getInputStream())
        .thenReturn(new DelegatingServletInputStream(new byte[0]));
    Vector<String> headerNames = new Vector<>();
    headerNames.add(HeaderConstants.X_CORRELATION_ID);
    Mockito.when(request.getHeaderNames()).thenReturn(headerNames.elements());
    Mockito.when(request.getHeaders(HeaderConstants.X_CORRELATION_ID))
        .thenReturn(Collections.enumeration(Collections.singletonList("corr-demo-1")));

    proxy.forward(wireMock.baseUrl(), request);

    wireMock.verify(
        getRequestedFor(urlEqualTo("/v1/analytics/hotspots"))
            .withHeader(HeaderConstants.X_CORRELATION_ID, equalTo("corr-demo-1")));
  }

  @Test
  void stripsContentEncodingWhenDownstreamClaimsGzip() throws Exception {
    // Mimic the Catalyst bug: decoded JSON body + leftover Content-Encoding: gzip.
    byte[] plainJson = "{\"accusedId\":\"ACC-00044\"}".getBytes(StandardCharsets.UTF_8);
    wireMock.stubFor(
        get(urlEqualTo("/v1/accusedPersons/ACC-00044:riskProfile"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Content-Encoding", "gzip")
                    .withBody(plainJson)));

    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getMethod()).thenReturn("GET");
    Mockito.when(request.getRequestURI()).thenReturn("/v1/accusedPersons/ACC-00044:riskProfile");
    Mockito.when(request.getQueryString()).thenReturn(null);
    Mockito.when(request.getInputStream())
        .thenReturn(new DelegatingServletInputStream(new byte[0]));
    Mockito.when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    ResponseEntity<byte[]> response = proxy.forward(wireMock.baseUrl(), request);

    assertEquals(200, response.getStatusCode().value());
    assertArrayEquals(plainJson, response.getBody());
    assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));
    assertEquals(
        null,
        response.getHeaders().getFirst("Content-Encoding"),
        "must not echo Content-Encoding when body bytes are already decoded");
  }

  @Test
  void stripsDownstreamCorsHeadersAndDoesNotForwardOrigin() throws Exception {
    wireMock.stubFor(
        get(urlEqualTo("/v1/auth/me"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Access-Control-Allow-Origin", "https://aparadhkavach-wb.onslate.in")
                    .withHeader("Access-Control-Allow-Credentials", "true")
                    .withHeader("X-Frame-Options", "ALLOW-FROM https://aparadhkavach-wb.onslate.in")
                    .withBody("{\"role\":\"INVESTIGATOR\"}")));

    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getMethod()).thenReturn("GET");
    Mockito.when(request.getRequestURI()).thenReturn("/v1/auth/me");
    Mockito.when(request.getQueryString()).thenReturn(null);
    Mockito.when(request.getInputStream())
        .thenReturn(new DelegatingServletInputStream(new byte[0]));
    Vector<String> headerNames = new Vector<>();
    headerNames.add("Origin");
    headerNames.add("Authorization");
    Mockito.when(request.getHeaderNames()).thenReturn(headerNames.elements());
    Mockito.when(request.getHeaders("Origin"))
        .thenReturn(
            Collections.enumeration(
                Collections.singletonList("https://aparadhkavach-wb.onslate.in")));
    Mockito.when(request.getHeaders("Authorization"))
        .thenReturn(Collections.enumeration(Collections.singletonList("Bearer test-token")));

    ResponseEntity<byte[]> response = proxy.forward(wireMock.baseUrl(), request);

    assertEquals(200, response.getStatusCode().value());
    assertEquals(null, response.getHeaders().getFirst("Access-Control-Allow-Origin"));
    assertEquals(null, response.getHeaders().getFirst("Access-Control-Allow-Credentials"));
    assertEquals(null, response.getHeaders().getFirst("X-Frame-Options"));
    assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));

    wireMock.verify(
        getRequestedFor(urlEqualTo("/v1/auth/me"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withoutHeader("Origin"));
  }

  /** Minimal ServletInputStream for mocked requests. */
  private static final class DelegatingServletInputStream
      extends jakarta.servlet.ServletInputStream {
    private final byte[] payload;
    private int index;

    private DelegatingServletInputStream(byte[] payload) {
      this.payload = payload;
    }

    @Override
    public int read() {
      return index < payload.length ? payload[index++] & 0xff : -1;
    }

    @Override
    public boolean isFinished() {
      return index >= payload.length;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(jakarta.servlet.ReadListener readListener) {}
  }
}
