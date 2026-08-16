package dev.aparadhkavach.orchestration.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.commons.exception.ExternalServiceException;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.stt.config.SarvamProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP client for {@code POST {SARVAM_STT_URL}/stt/transcribe}. Thin fail-fast wrapper — no
 * Resilience4j instance yet; callers surface 503 so text Q&amp;A stays usable.
 */
@Component
public class HttpSpeechToTextClient implements SpeechToTextClient {

  private static final Logger log = LoggerFactory.getLogger(HttpSpeechToTextClient.class);

  /** Must match STT {@code STT_INTERNAL_HEADER} / FastAPI alias. */
  static final String STT_INTERNAL_HEADER = "X-AparadhKavach-STT-Key";

  private final SarvamProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  /** Production ctor — must be marked; package 3-arg ctor is for tests only. */
  @Autowired
  public HttpSpeechToTextClient(SarvamProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, timedRestClient(properties));
  }

  HttpSpeechToTextClient(
      SarvamProperties properties, ObjectMapper objectMapper, RestClient restClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient = restClient;
  }

  private static RestClient timedRestClient(SarvamProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.max(500, properties.getConnectTimeoutMs()));
    factory.setReadTimeout(Math.max(5_000, properties.getReadTimeoutMs()));
    return RestClient.builder().requestFactory(factory).build();
  }

  @Override
  public TranscriptionResult transcribe(byte[] audioBytes, String filename, String languageHint) {
    if (audioBytes == null || audioBytes.length == 0) {
      throw new ValidationException("Audio is required");
    }
    String base = properties.getSttUrl() == null ? "" : properties.getSttUrl().trim();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (base.isBlank()) {
      throw new ExternalServiceException("Speech-to-text service is not configured");
    }
    String internalKey =
        properties.getSttInternalKey() == null ? "" : properties.getSttInternalKey().trim();
    if (internalKey.isBlank() || internalKey.startsWith("local-dev-placeholder")) {
      throw new ExternalServiceException(
          "Speech-to-text internal key is not configured (STT_INTERNAL_API_KEY)");
    }

    String name = filename == null || filename.isBlank() ? "audio.webm" : filename;
    String hint = languageHint == null || languageHint.isBlank() ? "en" : languageHint.trim();

    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder
        .part(
            "audio",
            new ByteArrayResource(audioBytes) {
              @Override
              public String getFilename() {
                return name;
              }
            })
        .contentType(MediaType.APPLICATION_OCTET_STREAM);
    builder.part("language_hint", hint);

    long started = System.currentTimeMillis();
    try {
      byte[] body =
          restClient
              .post()
              .uri(base + "/stt/transcribe")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .header(STT_INTERNAL_HEADER, internalKey)
              .body(builder.build())
              .retrieve()
              .body(byte[].class);
      TranscriptionResult result = parse(body);
      log.info(
          "stt transcribe OK chars={} conf={} lang={} tookMs={}",
          result.transcription() == null ? 0 : result.transcription().length(),
          result.confidence(),
          result.detectedLanguage(),
          System.currentTimeMillis() - started);
      return result;
    } catch (RestClientResponseException e) {
      log.warn(
          "stt transcribe HTTP {} tookMs={}: {}",
          e.getStatusCode().value(),
          System.currentTimeMillis() - started,
          e.getResponseBodyAsString());
      throw new ExternalServiceException(
          "Speech-to-text temporarily unavailable; type your follow-up instead");
    } catch (ExternalServiceException | ValidationException e) {
      throw e;
    } catch (Exception e) {
      log.warn(
          "stt transcribe failed tookMs={}: {}",
          System.currentTimeMillis() - started,
          e.toString());
      throw new ExternalServiceException(
          "Speech-to-text temporarily unavailable; type your follow-up instead");
    }
  }

  private TranscriptionResult parse(byte[] body) {
    if (body == null || body.length == 0) {
      throw new ExternalServiceException("Speech-to-text returned an empty body");
    }
    try {
      JsonNode root = objectMapper.readTree(body);
      String text = textOrEmpty(root, "transcription");
      if (text.isBlank()) {
        throw new ExternalServiceException("Speech-to-text returned an empty transcript");
      }
      double confidence = root.path("confidence").asDouble(0.85);
      String tier = textOrEmpty(root, "confidenceTier");
      if (tier.isBlank()) {
        tier = confidence >= 0.85 ? "high" : confidence < 0.70 ? "low" : "medium";
      }
      boolean needsConfirm =
          root.has("needsConfirmation")
              ? root.path("needsConfirmation").asBoolean(true)
              : !"high".equalsIgnoreCase(tier);
      String lang = textOrEmpty(root, "detectedLanguage");
      if (lang.isBlank()) {
        lang = textOrEmpty(root, "language");
      }
      if (lang.isBlank()) {
        lang = "en";
      }
      return new TranscriptionResult(text, confidence, tier, needsConfirm, lang);
    } catch (ExternalServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new ExternalServiceException("Speech-to-text response could not be parsed");
    }
  }

  private static String textOrEmpty(JsonNode root, String field) {
    JsonNode n = root.get(field);
    return n == null || n.isNull() ? "" : n.asText("").trim();
  }
}
