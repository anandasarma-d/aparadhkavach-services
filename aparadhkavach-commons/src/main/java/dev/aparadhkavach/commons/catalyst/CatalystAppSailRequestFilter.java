package dev.aparadhkavach.commons.catalyst;

import com.zc.auth.AuthHeaderProvider;
import com.zc.auth.CatalystSDK;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AppSail injects Catalyst project credentials on the inbound request. Bridging via {@link
 * AuthHeaderProvider} keeps us on jakarta.servlet (Spring Boot 3) while the SDK still sees the
 * headers it needs — {@code CatalystSDK.init(HttpServletRequest)} is javax.servlet-only.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CatalystAppSailRequestFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      CatalystSDK.init(new ServletAuthHeaderProvider(request));
    } catch (RuntimeException ignored) {
      // Local Self Client path / missing AppSail headers — ZcqlExecutor falls back to ZCProject.
    }
    filterChain.doFilter(request, response);
  }

  private static final class ServletAuthHeaderProvider implements AuthHeaderProvider {
    private final HttpServletRequest request;

    private ServletAuthHeaderProvider(HttpServletRequest request) {
      this.request = request;
    }

    @Override
    public String getHeaderValue(String headerName) {
      return request.getHeader(headerName);
    }
  }
}
