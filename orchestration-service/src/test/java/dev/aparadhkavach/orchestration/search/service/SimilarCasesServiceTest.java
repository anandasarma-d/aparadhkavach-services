package dev.aparadhkavach.orchestration.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.search.config.VectorProperties;
import dev.aparadhkavach.orchestration.search.model.SimilarCase;
import dev.aparadhkavach.orchestration.search.model.SimilarCasesResult;
import dev.aparadhkavach.orchestration.search.repository.FirEmbeddingsRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimilarCasesServiceTest {

  @Mock private FirEmbeddingsRepository repository;

  private VectorProperties properties;
  private SimilarCasesService service;

  @BeforeEach
  void setUp() {
    properties = new VectorProperties();
    properties.setTopK(5);
    properties.setMaxTopK(10);
    service = new SimilarCasesService(repository, properties);
  }

  @Test
  void clampsLimitToMaxTen() {
    when(repository.findSimilar(eq("FIR-000001"), eq(10))).thenReturn(List.of());

    SimilarCasesResult result = service.findSimilar("FIR-000001", 99);

    assertEquals(10, result.limit());
    verify(repository).findSimilar("FIR-000001", 10);
  }

  @Test
  void defaultsLimitWhenNull() {
    when(repository.findSimilar(eq("FIR-000001"), eq(5))).thenReturn(List.of());

    assertEquals(5, service.findSimilar("FIR-000001", null).limit());
  }

  @Test
  void clampsSubOneLimitToOne() {
    when(repository.findSimilar(eq("FIR-000001"), eq(1))).thenReturn(List.of());

    assertEquals(1, service.findSimilar("FIR-000001", 0).limit());
  }

  @Test
  void returnsNeighborsFromRepository() {
    SimilarCase neighbor =
        new SimilarCase(
            "FIR-000002",
            0.91,
            "Mysuru",
            "Vehicle theft",
            LocalDate.of(2024, 3, 12),
            "Under Investigation");
    when(repository.findSimilar(eq("FIR-000001"), eq(5))).thenReturn(List.of(neighbor));

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
    when(repository.findSimilar(eq("FIR-missing"), eq(5)))
        .thenThrow(new ResourceNotFoundException("No embedding for firId=FIR-missing"));

    assertThrows(ResourceNotFoundException.class, () -> service.findSimilar("FIR-missing", 5));
  }
}
