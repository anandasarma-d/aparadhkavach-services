package dev.aparadhkavach.investigation.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.commons.datastore.AccusedIdFormat;
import dev.aparadhkavach.commons.error.ApiErrorBody;
import dev.aparadhkavach.commons.error.ErrorCode;
import dev.aparadhkavach.commons.exception.ExternalServiceException;
import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Internal service-to-service client for Analytics {@code GET /v1/analytics/riskScores/{accusedId}}
 * (Section 5.6 ownership boundary — Investigation must not read {@code risk_scores} directly).
 */
@Component
public class AnalyticsRiskScoreClient {

  private final RestClient analyticsRestClient;
  private final ObjectMapper objectMapper;

  public AnalyticsRiskScoreClient(RestClient analyticsRestClient, ObjectMapper objectMapper) {
    this.analyticsRestClient = analyticsRestClient;
    this.objectMapper = objectMapper;
  }

  public AnalyticsRiskScoreResource getRiskScore(String accusedId) {
    String id = AccusedIdFormat.requireValid(accusedId);
    try {
      return analyticsRestClient
          .get()
          .uri("/v1/analytics/riskScores/{accusedId}", id)
          .accept(MediaType.APPLICATION_JSON)
          .retrieve()
          .body(AnalyticsRiskScoreResource.class);
    } catch (RestClientResponseException ex) {
      throw translate(ex, id);
    } catch (Exception ex) {
      throw new ExternalServiceException(
          "Analytics Service risk score call failed: " + ex.getMessage());
    }
  }

  private RuntimeException translate(RestClientResponseException ex, String accusedId) {
    HttpStatusCode status = ex.getStatusCode();
    if (status.value() == 404) {
      return new ResourceNotFoundException("No risk score for accusedId=" + accusedId);
    }
    ErrorCode remoteCode = readRemoteErrorCode(ex);
    if (remoteCode == ErrorCode.DOM_RESOURCE_NOT_FOUND) {
      return new ResourceNotFoundException("No risk score for accusedId=" + accusedId);
    }
    return new ExternalServiceException(
        "Analytics Service returned HTTP "
            + status.value()
            + " for accusedId="
            + accusedId
            + ": "
            + ex.getResponseBodyAsString(StandardCharsets.UTF_8));
  }

  private ErrorCode readRemoteErrorCode(RestClientResponseException ex) {
    try {
      byte[] body = ex.getResponseBodyAsByteArray();
      if (body.length == 0) {
        return null;
      }
      ApiErrorBody envelope = objectMapper.readValue(body, ApiErrorBody.class);
      if (envelope == null || envelope.error() == null || envelope.error().errorCode() == null) {
        return null;
      }
      return ErrorCode.valueOf(envelope.error().errorCode());
    } catch (IOException | IllegalArgumentException ignored) {
      return null;
    }
  }
}
