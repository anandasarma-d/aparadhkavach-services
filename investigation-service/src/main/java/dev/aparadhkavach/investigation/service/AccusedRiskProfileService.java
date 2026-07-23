package dev.aparadhkavach.investigation.service;

import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import dev.aparadhkavach.investigation.client.AnalyticsRiskScoreClient;
import dev.aparadhkavach.investigation.client.AnalyticsRiskScoreResource;
import dev.aparadhkavach.investigation.dto.AccusedRiskProfileResource;
import dev.aparadhkavach.investigation.model.AccusedPersonRecord;
import dev.aparadhkavach.investigation.repository.AccusedPersonsRepository;
import org.springframework.stereotype.Service;

/**
 * Composes accused DataStore fields (Investigation / Section 5.6) with Analytics risk_scores over
 * internal HTTP — no cross-table DataStore join.
 */
@Service
public class AccusedRiskProfileService {

  private final AccusedPersonsRepository accusedPersonsRepository;
  private final AnalyticsRiskScoreClient analyticsRiskScoreClient;

  public AccusedRiskProfileService(
      AccusedPersonsRepository accusedPersonsRepository,
      AnalyticsRiskScoreClient analyticsRiskScoreClient) {
    this.accusedPersonsRepository = accusedPersonsRepository;
    this.analyticsRiskScoreClient = analyticsRiskScoreClient;
  }

  public AccusedRiskProfileResource getRiskProfile(String accusedId) {
    AccusedPersonRecord accused =
        accusedPersonsRepository
            .findByAccusedId(accusedId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No accused_persons row for accusedId=" + accusedId));

    AnalyticsRiskScoreResource risk = analyticsRiskScoreClient.getRiskScore(accusedId);

    return new AccusedRiskProfileResource(
        accused.accusedId(),
        accused.name(),
        accused.addressDistrictId(),
        accused.priorOffenseCount(),
        risk.riskScore(),
        risk.topFeatureImportance(),
        risk.scoreId(),
        risk.scoredAt(),
        risk.pipelineRunId());
  }
}
