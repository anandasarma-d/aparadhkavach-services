package dev.aparadhkavach.orchestration.search.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FirEmbeddingsRepositoryTest {

  @Test
  void toPgVectorLiteral_formatsFloats() {
    assertEquals("[0.1,0.25,-1.5]", FirEmbeddingsRepository.toPgVectorLiteral(new float[] {0.1f, 0.25f, -1.5f}));
    assertEquals("[]", FirEmbeddingsRepository.toPgVectorLiteral(new float[] {}));
  }
}
