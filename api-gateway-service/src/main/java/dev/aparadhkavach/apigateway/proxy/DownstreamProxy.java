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

  /**
   * Headers that must not be forwarded as-is. Includes encoding/length: RestClient yields a decoded
   * body byte[], so echoing {@code Content-Encoding: gzip} makes browsers fail with
   * {@code ERR_CONTENT_DECODING_FAILED} / {@code TypeError: Failed to fetch} despite HTTP 200.
   */
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
          "content-length",
          "content-encoding",
          "accept-encoding");

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
        .headers(
            headers -> {
              copyRequestHeaders(request, headers);
              // Prefer uncompressed downstream bodies — we return raw bytes to the browser.
              headers.set(HttpHeaders.ACCEPT_ENCODING, "identity");
            })
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
