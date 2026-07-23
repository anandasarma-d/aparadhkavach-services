package dev.aparadhkavach.investigation.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.aparadhkavach.commons.exception.ExternalServiceException;
import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class AnalyticsRiskScoreClientTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private AnalyticsRiskScoreClient client;

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    RestClient restClient = RestClient.builder().baseUrl(wireMock.baseUrl()).build();
    client = new AnalyticsRiskScoreClient(restClient, mapper);
  }

  @Test
  void fetchesRiskScoreResource() {
    wireMock.stubFor(
        get(urlEqualTo("/v1/analytics/riskScores/ACC-00124"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "accusedId": "ACC-00124",
                          "riskScore": 78.0,
                          "topFeatureImportance": {
                            "offense_count": 0.38,
                            "recidivism_interval_avg": 0.29,
                            "district_spread": 0.18
                          },
                          "scoreId": "RISK-RUN-001-ACC-00124",
                          "scoredAt": "2026-07-01T09:00:00Z",
                          "pipelineRunId": "RUN-001"
                        }
                        """)));

    AnalyticsRiskScoreResource resource = client.getRiskScore("ACC-00124");

    assertEquals("ACC-00124", resource.accusedId());
    assertEquals(new BigDecimal("78.0"), resource.riskScore());
    assertEquals("RUN-001", resource.pipelineRunId());
  }

  @Test
  void mapsNotFound() {
    wireMock.stubFor(
        get(urlEqualTo("/v1/analytics/riskScores/ACC-missing"))
            .willReturn(aResponse().withStatus(404).withBody("{\"error\":{\"errorCode\":\"DOM_RESOURCE_NOT_FOUND\"}}")));

    assertThrows(ResourceNotFoundException.class, () -> client.getRiskScore("ACC-missing"));
  }

  @Test
  void mapsUpstreamFailure() {
    wireMock.stubFor(
        get(urlEqualTo("/v1/analytics/riskScores/ACC-00124"))
            .willReturn(aResponse().withStatus(503).withBody("down")));

    assertThrows(ExternalServiceException.class, () -> client.getRiskScore("ACC-00124"));
  }
}
