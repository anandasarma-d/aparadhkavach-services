package dev.aparadhkavach.apigateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.apigateway.config.JwtProperties;
import dev.aparadhkavach.commons.error.ApiError;
import dev.aparadhkavach.commons.error.ApiErrorBody;
import dev.aparadhkavach.commons.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Required JWT enforcement on /v1/** (mvp2/10). Skips /health, CORS preflight, and POST
 * /v1/auth/sessions (session mint).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtProperties jwtProperties;
  private final RolePathAllowlist rolePathAllowlist;
  private final ObjectMapper objectMapper;
  private volatile RSAPublicKey publicKey;

  public JwtAuthFilter(
      JwtProperties jwtProperties, RolePathAllowlist rolePathAllowlist, ObjectMapper objectMapper) {
    this.jwtProperties = jwtProperties;
    this.rolePathAllowlist = rolePathAllowlist;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String method = request.getMethod();
    if (HttpMethod.OPTIONS.matches(method)) {
      return true;
    }
    String path = request.getRequestURI();
    if ("/health".equals(path)) {
      return true;
    }
    // Session mint must be reachable without a Bearer token.
    if (HttpMethod.POST.matches(method) && "/v1/auth/sessions".equals(path)) {
      return true;
    }
    return !path.startsWith("/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (!StringUtils.hasText(header) || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, "Missing Bearer token");
      return;
    }
    String token = header.substring(7).trim();
    final Claims claims;
    try {
      claims = Jwts.parser().verifyWith(publicKey()).build().parseSignedClaims(token).getPayload();
    } catch (Exception e) {
      writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, "Invalid or expired token");
      return;
    }

    String role = claims.get("role", String.class);
    if (!rolePathAllowlist.isAllowed(role, request.getRequestURI())) {
      writeError(
          response,
          HttpServletResponse.SC_FORBIDDEN,
          ErrorCode.AUTH_FORBIDDEN,
          "Role " + role + " is not allowed for " + request.getRequestURI());
      return;
    }

    filterChain.doFilter(request, response);
  }

  private RSAPublicKey publicKey() {
    RSAPublicKey local = publicKey;
    if (local == null) {
      synchronized (this) {
        local = publicKey;
        if (local == null) {
          local = PublicPemReader.readPublicKey(jwtProperties.getPublicKey());
          publicKey = local;
        }
      }
    }
    return local;
  }

  private void writeError(HttpServletResponse response, int status, ErrorCode code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    String reason =
        status == 401
            ? "UNAUTHORIZED"
            : HttpServletResponse.SC_FORBIDDEN == status ? "FORBIDDEN" : "ERROR";
    ApiError error =
        new ApiError(
            status,
            reason,
            code.name(),
            message,
            null,
            Span.current().getSpanContext().getTraceId());
    objectMapper.writeValue(response.getOutputStream(), new ApiErrorBody(error));
  }
}
