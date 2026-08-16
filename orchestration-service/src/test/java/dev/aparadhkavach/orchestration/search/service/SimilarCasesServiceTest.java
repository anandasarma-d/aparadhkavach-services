package dev.aparadhkavach.orchestration.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.search.config.VectorProperties;
import dev.aparadhkavach.orchestration.search.model.FirTextSearchResult;
import dev.aparadhkavach.orchestration.search.model.SimilarCase;
import dev.aparadhkavach.orchestration.search.model.SimilarCasesResult;
import dev.aparadhkavach.orchestration.search.repository.FirEmbeddingsRepository;
import dev.aparadhkavach.orchestration.search.voyage.VoyageEmbeddingClient;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Stub-based (no Mockito) — JDK 25 / sandbox cannot attach ByteBuddy MockMaker. */
class SimilarCasesServiceTest {

  private VectorProperties properties;
  private RecordingRepo repository;
  private RecordingVoyage voyage;
  private SimilarCasesService service;

  @BeforeEach
  void setUp() {
    properties = new VectorProperties();
    properties.setTopK(5);
    properties.setMaxTopK(10);
    repository = new RecordingRepo();
    voyage = new RecordingVoyage();
    service = new SimilarCasesService(repository, properties, voyage);
  }

  @Test
  void clampsLimitToMaxTen() {
    SimilarCasesResult result = service.findSimilar("FIR-000001", 99);
    assertEquals(10, result.limit());
    assertEquals(10, repository.lastLimit);
  }

  @Test
  void defaultsLimitWhenNull() {
    assertEquals(5, service.findSimilar("FIR-000001", null).limit());
  }

  @Test
  void clampsSubOneLimitToOne() {
    assertEquals(1, service.findSimilar("FIR-000001", 0).limit());
  }

  @Test
  void returnsNeighborsFromRepository() {
    repository.firNeighbors =
        List.of(
            new SimilarCase(
                "FIR-000002",
                0.91,
                "Mysuru",
                "Vehicle theft",
                LocalDate.of(2024, 3, 12),
                "Under Investigation"));

    SimilarCasesResult result = service.findSimilar("FIR-000001", 5);

    assertEquals("FIR-000001", result.firId());
    assertEquals(1, result.similarCases().size());
    assertEquals("FIR-000002", result.similarCases().get(0).firId());
    assertTrue(result.similarCases().get(0).similarityScore() > 0.9);
  }

  @Test
  void rejectsBlankFirId() {
    assertThrows(ValidationException.class, () -> service.findSimilar("  ", 5));
  }

  @Test
  void propagatesNotFound() {
    repository.throwNotFound = true;
    assertThrows(ResourceNotFoundException.class, () -> service.findSimilar("FIR-missing", 5));
  }

  @Test
  void searchByText_embedsThenAnn() {
    float[] vector = new float[] {0.1f, 0.2f};
    voyage.vector = vector;
    repository.vectorNeighbors =
        List.of(new SimilarCase("FIR-000010", 0.88, "Mandya", "Vehicle theft", null, "UI"));

    FirTextSearchResult result = service.searchByText("vehicle theft at night", 5);

    assertEquals("vehicle theft at night", result.query());
    assertEquals(1, result.similarCases().size());
    assertEquals("FIR-000010", result.similarCases().get(0).firId());
    assertEquals("vehicle theft at night", voyage.lastText.get());
    assertEquals(vector, repository.lastEmbedding);
    assertEquals(5, repository.lastVectorLimit);
  }

  @Test
  void searchByText_dropsNeighborsBelowMinSimilarity() {
    properties.setTextMinSimilarity(0.50);
    float[] vector = new float[] {0.1f};
    voyage.vector = vector;
    repository.vectorNeighbors =
        List.of(
            new SimilarCase("FIR-STRONG", 0.62, "Mysuru", "Vehicle theft", null, "UI"),
            new SimilarCase("FIR-GRAY", 0.49, "Mysuru", "Robbery / dacoity", null, "UI"),
            new SimilarCase("FIR-WEAK", 0.37, "Mysuru", "Theft", null, "UI"));

    FirTextSearchResult result = service.searchByText("murder near bus stand last month", 5);

    assertEquals(1, result.similarCases().size());
    assertEquals("FIR-STRONG", result.similarCases().get(0).firId());
  }

  @Test
  void searchByText_emptyWhenAllBelowThreshold() {
    properties.setTextMinSimilarity(0.50);
    voyage.vector = new float[] {0.1f};
    repository.vectorNeighbors =
        List.of(
            new SimilarCase("FIR-GRAY", 0.49, "Mysuru", "Robbery / dacoity", null, "UI"),
            new SimilarCase("FIR-WEAK", 0.37, "Mysuru", "Theft", null, "UI"));

    FirTextSearchResult result = service.searchByText("murder near bus stand last month", 5);

    assertTrue(result.similarCases().isEmpty());
  }

  @Test
  void searchByText_rejectsSingleWord() {
    assertThrows(ValidationException.class, () -> service.searchByText("theft", 5));
    assertThrows(ValidationException.class, () -> service.searchByText("short", 5));
  }

  @Test
  void searchByText_rejectsBlank() {
    assertThrows(ValidationException.class, () -> service.searchByText("  ", 5));
  }

  /** Minimal stub — only the methods SimilarCasesService calls. */
  private static final class RecordingRepo extends FirEmbeddingsRepository {
    List<SimilarCase> firNeighbors = List.of();
    List<SimilarCase> vectorNeighbors = List.of();
    boolean throwNotFound;
    int lastLimit;
    float[] lastEmbedding;
    int lastVectorLimit;

    RecordingRepo() {
      super(null);
    }

    @Override
    public List<SimilarCase> findSimilar(String firId, int limit) {
      lastLimit = limit;
      if (throwNotFound) {
        throw new ResourceNotFoundException("No embedding for firId=" + firId);
      }
      return firNeighbors;
    }

    @Override
    public List<SimilarCase> findSimilarByEmbedding(float[] embedding, int limit) {
      lastEmbedding = embedding;
      lastVectorLimit = limit;
      return vectorNeighbors;
    }
  }

  private static final class RecordingVoyage implements VoyageEmbeddingClient {
    float[] vector = new float[] {0.0f};
    final AtomicReference<String> lastText = new AtomicReference<>();

    @Override
    public float[] embedQuery(String text) {
      lastText.set(text);
      return vector;
    }
  }
}
