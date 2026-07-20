package dev.aparadhkavach.apigateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code CORS_ALLOWED_ORIGINS} - comma-separated, environment-driven (ADR-022). One list per
 * environment (dev/staging/prod each set their own aparadhkavach-client origin(s)); no wildcard, no
 * hardcoded value here.
 */
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

  private List<String> allowedOrigins;

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }
}
