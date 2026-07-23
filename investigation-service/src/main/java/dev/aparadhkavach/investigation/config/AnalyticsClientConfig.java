package dev.aparadhkavach.investigation.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AnalyticsClientConfig {

  @Bean
  RestClient analyticsRestClient(
      AnalyticsClientProperties properties, RestTemplateBuilder restTemplateBuilder) {
    return RestClient.builder()
        .baseUrl(properties.getBaseUrl())
        .requestFactory(restTemplateBuilder.build().getRequestFactory())
        .build();
  }
}
