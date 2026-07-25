package dev.aparadhkavach.apigateway.config;

import dev.aparadhkavach.commons.header.HeaderConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API Gateway is the sole client-facing entry point (Section 3.2) - aparadhkavach-client never
 * calls other services directly, so CORS only needs configuring here. Allowed origins come from
 * {@link CorsProperties}, environment-driven (ADR-022), never hardcoded, and reflected back
 * explicitly per request rather than wildcarded, since requests carry an {@code Authorization}
 * header.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private final CorsProperties corsProperties;

  public CorsConfig(CorsProperties corsProperties) {
    this.corsProperties = corsProperties;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders(HeaderConstants.X_CORRELATION_ID)
        .allowCredentials(true);
  }
}
