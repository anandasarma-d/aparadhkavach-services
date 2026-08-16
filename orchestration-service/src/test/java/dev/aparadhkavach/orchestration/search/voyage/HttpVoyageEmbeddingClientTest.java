package dev.aparadhkavach.orchestration.search.voyage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.commons.exception.ExternalServiceException;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.config.VoyageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

class HttpVoyageEmbeddingClientTest {

  @Test
  void embedQuery_parsesVector() {
    VoyageProperties properties = new VoyageProperties();
    properties.setApiKey("test-key");
    properties.setEmbeddingModel("voyage-3-large");

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(MockRestRequestMatchers.requestTo("https://api.voyageai.com/v1/embeddings"))
        .andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {"data":[{"embedding":[0.1,0.2,0.3],"index":0}],"model":"voyage-3-large"}
                """,
                MediaType.APPLICATION_JSON));

    HttpVoyageEmbeddingClient client =
        new HttpVoyageEmbeddingClient(properties, new ObjectMapper(), builder.build());
    float[] vector = client.embedQuery("vehicle theft");
    assertEquals(3, vector.length);
    assertEquals(0.1f, vector[0], 1e-6);
    server.verify();
  }

  @Test
  void embedQuery_rejectsBlank() {
    VoyageProperties properties = new VoyageProperties();
    properties.setApiKey("test-key");
    HttpVoyageEmbeddingClient client =
        new HttpVoyageEmbeddingClient(properties, new ObjectMapper(), RestClient.create());
    assertThrows(ValidationException.class, () -> client.embedQuery("  "));
  }

  @Test
  void embedQuery_missingKey() {
    VoyageProperties properties = new VoyageProperties();
    properties.setApiKey("local-dev-placeholder-not-a-real-key");
    HttpVoyageEmbeddingClient client =
        new HttpVoyageEmbeddingClient(properties, new ObjectMapper(), RestClient.create());
    assertThrows(ExternalServiceException.class, () -> client.embedQuery("theft"));
  }
}
