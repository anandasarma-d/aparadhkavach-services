package dev.aparadhkavach.investigation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import dev.aparadhkavach.investigation.client.AnalyticsRiskScoreClient;
import dev.aparadhkavach.investigation.client.AnalyticsRiskScoreResource;
import dev.aparadhkavach.investigation.dto.AccusedRiskProfileResource;
import dev.aparadhkavach.investigation.model.AccusedFeaturesRecord;
import dev.aparadhkavach.investigation.model.AccusedPersonRecord;
import dev.aparadhkavach.investigation.repository.AccusedFeaturesRepository;
import dev.aparadhkavach.investigation.repository.AccusedPersonsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccusedRiskProfileServiceTest {

  @Mock private AccusedPersonsRepository accusedPersonsRepository;
  @Mock private AccusedFeaturesRepository accusedFeaturesRepository;
  @Mock private AnalyticsRiskScoreClient analyticsRiskScoreClient;

  @InjectMocks private AccusedRiskProfileService accusedRiskProfileService;

  @Test
  void composesAccusedAndRiskScore() {
    when(accusedPersonsRepository.findByAccusedId("ACC-00124"))
        .thenReturn(Optional.of(new AccusedPersonRecord("ACC-00124", "Ravi Kumar", "DIST-MYS", 3)));
    when(analyticsRiskScoreClient.getRiskScore("ACC-00124"))
        .thenReturn(
            new AnalyticsRiskScoreResource(
                "ACC-00124",
                new BigDecimal("78.0"),
                Map.of(
                    "offense_count",
                    0.38,
                    "recidivism_interval_avg",
                    0.29,
                    "district_spread",
                    0.18),
                "RISK-RUN-001-ACC-00124",
                Instant.parse("2026-07-01T09:00:00Z"),
                "RUN-001"));
    when(accusedFeaturesRepository.findByAccusedId("ACC-00124"))
        .thenReturn(
            Optional.of(
                new AccusedFeaturesRecord("ACC-00124", 3, new BigDecimal("125"), 4, 2, 1, 200)));

    AccusedRiskProfileResource profile = accusedRiskProfileService.getRiskProfile("ACC-00124");

    assertEquals("ACC-00124", profile.accusedId());
    assertEquals("Ravi Kumar", profile.name());
    assertEquals("DIST-MYS", profile.addressDistrictId());
    assertEquals(3, profile.priorOffenseCount());
    assertEquals(new BigDecimal("78.0"), profile.riskScore());
    assertEquals("RUN-001", profile.pipelineRunId());
    assertEquals(3, profile.caseDrivers().offenseCount());
    assertEquals(4, profile.caseDrivers().crimeTypeSeverityMax());
    assertEquals(2, profile.caseDrivers().districtSpread());
    assertEquals(1, profile.caseDrivers().coAccusedCount());
    assertEquals(200, profile.caseDrivers().daysSinceLastOffense());
  }

  @Test
  void composesWithoutCaseDriversWhenFeaturesAbsent() {
    when(accusedPersonsRepository.findByAccusedId("ACC-00050"))
        .thenReturn(Optional.of(new AccusedPersonRecord("ACC-00050", "Meena Rao", "DIST-BLR", 1)));
    when(analyticsRiskScoreClient.getRiskScore("ACC-00050"))
        .thenReturn(
            new AnalyticsRiskScoreResource(
                "ACC-00050",
                new BigDecimal("14.0"),
                Map.of(),
                "RISK-RUN-001-ACC-00050",
                Instant.parse("2026-07-01T09:00:00Z"),
                "RUN-001"));
    when(accusedFeaturesRepository.findByAccusedId("ACC-00050")).thenReturn(Optional.empty());

    AccusedRiskProfileResource profile = accusedRiskProfileService.getRiskProfile("ACC-00050");

    assertEquals("ACC-00050", profile.accusedId());
    assertNull(profile.caseDrivers());
  }

  @Test
  void throwsWhenAccusedMissing() {
    when(accusedPersonsRepository.findByAccusedId("ACC-missing")).thenReturn(Optional.empty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> accusedRiskProfileService.getRiskProfile("ACC-missing"));
  }
}
