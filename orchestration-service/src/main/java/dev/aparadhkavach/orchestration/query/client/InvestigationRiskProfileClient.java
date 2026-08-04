package dev.aparadhkavach.orchestration.query.client;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Investigation HTTP for accused DataStore-backed risk context (mvp2/11). Soft-fails on transport
 * errors so Neo4j-only answers remain possible when Investigation is cold or times out.
 */
@Component
public class InvestigationRiskProfileClient {

  private static final Logger log = LoggerFactory.getLogger(InvestigationRiskProfileClient.class);

  private final RestClient investigationRestClient;

  public InvestigationRiskProfileClient(
      @Qualifier("investigationRestClient") RestClient investigationRestClient) {
    this.investigationRestClient = investigationRestClient;
  }

  public Optional<InvestigationRiskProfileSnapshot> findRiskProfile(String accusedId) {
    long started = System.currentTimeMillis();
    log.info("Investigation riskProfile start accusedId={}", accusedId);
    try {
      InvestigationRiskProfileSnapshot body =
          investigationRestClient
              .get()
              .uri("/v1/accusedPersons/{id}:riskProfile", accusedId)
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(InvestigationRiskProfileSnapshot.class);
      log.info(
          "Investigation riskProfile ok accusedId={} tookMs={}",
          accusedId,
          System.currentTimeMillis() - started);
      return Optional.ofNullable(body);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        log.info(
            "Investigation has no risk profile for accusedId={} tookMs={}",
            accusedId,
            System.currentTimeMillis() - started);
        return Optional.empty();
      }
      log.warn(
          "Investigation riskProfile failed for accusedId={} status={} tookMs={}: {}",
          accusedId,
          ex.getStatusCode().value(),
          System.currentTimeMillis() - started,
          ex.getMessage());
      return Optional.empty();
    } catch (Exception ex) {
      log.warn(
          "Investigation riskProfile unreachable for accusedId={} tookMs={}: {}",
          accusedId,
          System.currentTimeMillis() - started,
          ex.toString());
      return Optional.empty();
    }
  }
}
