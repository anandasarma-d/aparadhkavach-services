package dev.aparadhkavach.apigateway.proxy;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Minimal request forwarder for Feature 1 path prefixes. Not a general-purpose reverse proxy —
 * copies method, path, query, body, and non-hop-by-hop headers to a configured downstream base URL.
 */
@Component
public class DownstreamProxy {

  private static final Set<String> HOP_BY_HOP_HEADERS =
      Set.of(
          "connection",
          "keep-alive",
          "proxy-authenticate",
          "proxy-authorization",
          "te",
          "trailers",
          "transfer-encoding",
          "upgrade",
          "host",
          "content-length");

  private final RestClient restClient;

  public DownstreamProxy() {
    this.restClient = RestClient.create();
  }

  /** Package-visible for tests that need to inject a RestClient (e.g. WireMock). */
  DownstreamProxy(RestClient restClient) {
    this.restClient = restClient;
  }

  public ResponseEntity<byte[]> forward(String downstreamBaseUrl, HttpServletRequest request)
      throws IOException {
    String base =
        downstreamBaseUrl.endsWith("/")
            ? downstreamBaseUrl.substring(0, downstreamBaseUrl.length() - 1)
            : downstreamBaseUrl;
    String query = request.getQueryString();
    String targetUri =
        base + request.getRequestURI() + (query != null ? "?" + query : "");

    byte[] body = request.getInputStream().readAllBytes();
    HttpMethod method = HttpMethod.valueOf(request.getMethod());

    return restClient
        .method(method)
        .uri(targetUri)
        .headers(headers -> copyRequestHeaders(request, headers))
        .body(body)
        .exchange(
            (req, res) -> {
              HttpHeaders responseHeaders = new HttpHeaders();
              res.getHeaders()
                  .forEach(
                      (name, values) -> {
                        if (!isHopByHop(name)) {
                          responseHeaders.put(name, values);
                        }
                      });
              byte[] responseBody =
                  res.getBody() != null ? res.getBody().readAllBytes() : new byte[0];
              return ResponseEntity.status(res.getStatusCode())
                  .headers(responseHeaders)
                  .body(responseBody);
            });
  }

  private static void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
    Collections.list(request.getHeaderNames())
        .forEach(
            name -> {
              if (!isHopByHop(name)) {
                Collections.list(request.getHeaders(name))
                    .forEach(value -> headers.add(name, value));
              }
            });
  }

  private static boolean isHopByHop(String headerName) {
    return HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
  }
}
