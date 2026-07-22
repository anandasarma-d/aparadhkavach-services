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
    Mockito.when(request.getRequestURI())
        .thenReturn("/v1/accusedPersons/ACC-00124:riskProfile");
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
