package dev.aparadhkavach.orchestration.query.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Hard timeouts for Spring AI Anthropic's {@code RestClient} (mvp2/11). Without these, outbound
 * calls to {@code api.anthropic.com} can hang until Catalyst AppSail returns Gateway 408 — Orch
 * logs stop at {@code Claude ask start} with no completion line.
 *
 * <p>Spring AI Anthropic injects {@code ObjectProvider<RestClient.Builder>} — Boot's builder
 * (customized here) or our fallback bean below.
 */
@Configuration
public class ClaudeHttpTimeoutConfig {

  private static final Logger log = LoggerFactory.getLogger(ClaudeHttpTimeoutConfig.class);

  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  static final Duration READ_TIMEOUT = Duration.ofSeconds(12);

  @Bean
  RestClientCustomizer claudeAnthropicRestClientCustomizer() {
    log.info(
        "Claude/Anthropic RestClient timeouts connect={}s read={}s",
        CONNECT_TIMEOUT.toSeconds(),
        READ_TIMEOUT.toSeconds());
    return builder -> builder.requestFactory(timeoutRequestFactory());
  }

  /**
   * Fallback when Boot does not expose a {@link RestClient.Builder} bean — AnthropicApi still needs
   * a timed-out builder via {@code ObjectProvider.getIfAvailable}.
   */
  @Bean
  @ConditionalOnMissingBean(RestClient.Builder.class)
  RestClient.Builder restClientBuilder() {
    return RestClient.builder().requestFactory(timeoutRequestFactory());
  }

  private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }
}
