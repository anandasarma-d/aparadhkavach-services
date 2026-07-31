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
 *
 * <p>On Catalyst AppSail the platform edge already injects {@code Access-Control-Allow-Origin}
 * for the request {@code Origin}. Enabling Spring CORS there stacks a second value and browsers
 * fail with "multiple values" even when {@code CORS_ALLOWED_ORIGINS} lists each origin once.
 * Detect AppSail via {@code X_ZOHO_CATALYST_LISTEN_PORT} and skip Spring mappings.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private final CorsProperties corsProperties;

  public CorsConfig(CorsProperties corsProperties) {
    this.corsProperties = corsProperties;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (System.getenv("X_ZOHO_CATALYST_LISTEN_PORT") != null) {
      return;
    }
    registry
        .addMapping("/**")
        .allowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders(HeaderConstants.X_CORRELATION_ID)
        .allowCredentials(true);
  }
}
