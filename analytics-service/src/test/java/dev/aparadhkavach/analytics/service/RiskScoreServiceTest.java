package dev.aparadhkavach.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import dev.aparadhkavach.analytics.config.RiskProperties;
import dev.aparadhkavach.analytics.dto.RiskScoreResource;
import dev.aparadhkavach.analytics.model.RiskScoreRecord;
import dev.aparadhkavach.analytics.repository.RiskScoreRepository;
import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskScoreServiceTest {

  @Mock private RiskScoreRepository riskScoreRepository;

  private RiskScoreService riskScoreService;

  @BeforeEach
  void setUp() {
    RiskProperties properties = new RiskProperties();
    properties.setTopFeatureCount(3);
    riskScoreService = new RiskScoreService(riskScoreRepository, properties);
  }

  @Test
  void returnsTopThreeFeatureImportance() {
    Map<String, Double> features = new LinkedHashMap<>();
    features.put("offense_count", 0.38);
    features.put("recidivism_interval_avg", 0.29);
    features.put("district_spread", 0.18);
    features.put("co_accused_count", 0.05);

    when(riskScoreRepository.findLatestByAccusedId("ACC-00124"))
        .thenReturn(
            Optional.of(
                new RiskScoreRecord(
                    "RISK-RUN-001-ACC-00124",
                    "ACC-00124",
                    new BigDecimal("78.0"),
                    features,
                    "RUN-001",
                    Instant.parse("2026-07-01T09:00:00Z"))));

    RiskScoreResource resource = riskScoreService.getByAccusedId("ACC-00124");

    assertEquals("ACC-00124", resource.accusedId());
    assertEquals(new BigDecimal("78.0"), resource.riskScore());
    assertEquals(3, resource.topFeatureImportance().size());
    assertEquals(0.38, resource.topFeatureImportance().get("offense_count"), 1e-9);
    assertEquals("RISK-RUN-001-ACC-00124", resource.scoreId());
  }

  @Test
  void throwsWhenMissing() {
    when(riskScoreRepository.findLatestByAccusedId("ACC-missing")).thenReturn(Optional.empty());
    assertThrows(
        ResourceNotFoundException.class, () -> riskScoreService.getByAccusedId("ACC-missing"));
  }
}
