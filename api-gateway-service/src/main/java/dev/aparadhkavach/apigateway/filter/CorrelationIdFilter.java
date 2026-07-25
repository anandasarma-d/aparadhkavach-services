package dev.aparadhkavach.apigateway.filter;

import dev.aparadhkavach.commons.header.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every gateway request has {@link HeaderConstants#X_CORRELATION_ID}: reuse the inbound
 * value when present, otherwise generate a UUID. Echoes the id on the response and exposes it on
 * the wrapped request so {@code DownstreamProxy} forwards it unchanged (Section 9.2b / A8).
 *
 * <p>Does <strong>not</strong> feed {@code ApiError.traceId} — that stays OTel span context
 * (ADR-009).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

  static final String MDC_KEY = "correlation_id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String incoming = request.getHeader(HeaderConstants.X_CORRELATION_ID);
    String correlationId =
        StringUtils.hasText(incoming) ? incoming.trim() : UUID.randomUUID().toString();

    HttpServletRequest toForward =
        StringUtils.hasText(incoming)
            ? request
            : new CorrelationIdRequestWrapper(request, correlationId);

    response.setHeader(HeaderConstants.X_CORRELATION_ID, correlationId);
    MDC.put(MDC_KEY, correlationId);
    try {
      filterChain.doFilter(toForward, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  /** Makes a generated correlation id visible via the usual servlet header APIs. */
  static final class CorrelationIdRequestWrapper extends HttpServletRequestWrapper {
    private final String correlationId;

    CorrelationIdRequestWrapper(HttpServletRequest request, String correlationId) {
      super(request);
      this.correlationId = correlationId;
    }

    @Override
    public String getHeader(String name) {
      if (HeaderConstants.X_CORRELATION_ID.equalsIgnoreCase(name)) {
        return correlationId;
      }
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (HeaderConstants.X_CORRELATION_ID.equalsIgnoreCase(name)) {
        return Collections.enumeration(Collections.singletonList(correlationId));
      }
      return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Set<String> names = new LinkedHashSet<>();
      Enumeration<String> existing = super.getHeaderNames();
      if (existing != null) {
        while (existing.hasMoreElements()) {
          names.add(existing.nextElement());
        }
      }
      names.add(HeaderConstants.X_CORRELATION_ID);
      return Collections.enumeration(names);
    }
  }
}
