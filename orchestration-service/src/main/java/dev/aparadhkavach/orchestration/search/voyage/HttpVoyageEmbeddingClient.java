package dev.aparadhkavach.orchestration.search.voyage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.commons.exception.ExternalServiceException;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.config.VoyageProperties;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP client for {@code POST https://api.voyageai.com/v1/embeddings} (typed-text similar / Auto/18
 * free-text path).
 */
@Component
public class HttpVoyageEmbeddingClient implements VoyageEmbeddingClient {

  private static final Logger log = LoggerFactory.getLogger(HttpVoyageEmbeddingClient.class);
  private static final String EMBEDDINGS_URL = "https://api.voyageai.com/v1/embeddings";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
  private static final int MAX_QUERY_CHARS = 500;
  private static final int EXPECTED_DIMS = 1024;

  private final VoyageProperties voyageProperties;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public HttpVoyageEmbeddingClient(VoyageProperties voyageProperties, ObjectMapper objectMapper) {
    this(voyageProperties, objectMapper, timedRestClient());
  }

  /** Package-visible for tests (inject a MockRestServiceServer-backed client). */
  HttpVoyageEmbeddingClient(
      VoyageProperties voyageProperties, ObjectMapper objectMapper, RestClient restClient) {
    this.voyageProperties = voyageProperties;
    this.objectMapper = objectMapper;
    this.restClient = restClient;
  }

  private static RestClient timedRestClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().requestFactory(factory).build();
  }

  @Override
  public float[] embedQuery(String text) {
    String query = text == null ? "" : text.trim();
    if (query.isBlank()) {
      throw new ValidationException("Search text is required");
    }
    if (query.length() > MAX_QUERY_CHARS) {
      throw new ValidationException(
          "Search text is too long (max " + MAX_QUERY_CHARS + " characters)");
    }
    String apiKey = voyageProperties.getApiKey();
    if (apiKey == null
        || apiKey.isBlank()
        || apiKey.startsWith("local-dev-placeholder")) {
      throw new ExternalServiceException(
          "Embedding service is not configured (missing Voyage API key)");
    }
    String model =
        voyageProperties.getEmbeddingModel() == null || voyageProperties.getEmbeddingModel().isBlank()
            ? "voyage-3-large"
            : voyageProperties.getEmbeddingModel().trim();

    VoyageEmbedRequest body = new VoyageEmbedRequest(List.of(query), model, "query");
    long start = System.currentTimeMillis();
    try {
      String raw =
          restClient
              .post()
              .uri(EMBEDDINGS_URL)
              .contentType(MediaType.APPLICATION_JSON)
              .header("Authorization", "Bearer " + apiKey)
              .body(body)
              .retrieve()
              .body(String.class);
      float[] vector = parseEmbedding(raw);
      log.info(
          "voyage embed OK model={} chars={} dims={} tookMs={}",
          model,
          query.length(),
          vector.length,
          System.currentTimeMillis() - start);
      return vector;
    } catch (RestClientResponseException e) {
      log.warn(
          "voyage embed HTTP {} tookMs={}: {}",
          e.getStatusCode().value(),
          System.currentTimeMillis() - start,
          e.getMessage());
      throw new ExternalServiceException(
          "Embedding service returned HTTP " + e.getStatusCode().value());
    } catch (ExternalServiceException | ValidationException e) {
      throw e;
    } catch (Exception e) {
      log.warn(
          "voyage embed failed tookMs={}: {}",
          System.currentTimeMillis() - start,
          e.getMessage());
      throw new ExternalServiceException("Embedding service call failed");
    }
  }

  private float[] parseEmbedding(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ExternalServiceException("Embedding service returned an empty body");
    }
    try {
      VoyageEmbedResponse response = objectMapper.readValue(raw, VoyageEmbedResponse.class);
      if (response.data() == null || response.data().isEmpty()) {
        throw new ExternalServiceException("Embedding service returned no vectors");
      }
      List<Double> values = response.data().get(0).embedding();
      if (values == null || values.isEmpty()) {
        throw new ExternalServiceException("Embedding service returned an empty vector");
      }
      if (values.size() != EXPECTED_DIMS) {
        log.warn("voyage embed unexpected dims={} (expected {})", values.size(), EXPECTED_DIMS);
      }
      float[] out = new float[values.size()];
      for (int i = 0; i < values.size(); i++) {
        Double v = values.get(i);
        out[i] = v == null ? 0f : v.floatValue();
      }
      return out;
    } catch (ExternalServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new ExternalServiceException("Embedding service response could not be parsed");
    }
  }

  public record VoyageEmbedRequest(
      List<String> input,
      String model,
      @JsonProperty("input_type") String inputType) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record VoyageEmbedResponse(List<VoyageEmbedDatum> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record VoyageEmbedDatum(List<Double> embedding) {}
}
