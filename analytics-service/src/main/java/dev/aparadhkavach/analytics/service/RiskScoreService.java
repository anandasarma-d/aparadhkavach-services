package dev.aparadhkavach.analytics.service;

import dev.aparadhkavach.analytics.config.RiskProperties;
import dev.aparadhkavach.analytics.dto.RiskScoreResource;
import dev.aparadhkavach.analytics.model.RiskScoreRecord;
import dev.aparadhkavach.analytics.repository.RiskScoreRepository;
import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import dev.aparadhkavach.commons.risk.TopFeatureImportanceSelector;
import org.springframework.stereotype.Service;

@Service
public class RiskScoreService {

  private final RiskScoreRepository riskScoreRepository;
  private final RiskProperties riskProperties;

  public RiskScoreService(RiskScoreRepository riskScoreRepository, RiskProperties riskProperties) {
    this.riskScoreRepository = riskScoreRepository;
    this.riskProperties = riskProperties;
  }

  public RiskScoreResource getByAccusedId(String accusedId) {
    RiskScoreRecord record =
        riskScoreRepository
            .findLatestByAccusedId(accusedId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No risk_scores row for accusedId=" + accusedId));

    return new RiskScoreResource(
        record.accusedId(),
        record.riskScore(),
        TopFeatureImportanceSelector.selectTop(
            record.featureImportance(), riskProperties.getTopFeatureCount()),
        record.scoreId(),
        record.scoredAt(),
        record.pipelineRunId());
  }
}
