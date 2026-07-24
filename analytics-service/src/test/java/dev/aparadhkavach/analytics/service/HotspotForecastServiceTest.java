package dev.aparadhkavach.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import dev.aparadhkavach.analytics.dto.HotspotsPageResource;
import dev.aparadhkavach.analytics.model.HotspotForecastRecord;
import dev.aparadhkavach.analytics.repository.HotspotForecastRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HotspotForecastServiceTest {

  @Mock private HotspotForecastRepository hotspotForecastRepository;

  private HotspotForecastService hotspotForecastService;

  @BeforeEach
  void setUp() {
    hotspotForecastService = new HotspotForecastService(hotspotForecastRepository);
  }

  @Test
  void returnsEmptyListWhenTableEmpty() {
    when(hotspotForecastRepository.findAllOrderedByScoreDesc()).thenReturn(List.of());

    HotspotsPageResource page = hotspotForecastService.listHotspots(50, null);

    assertEquals(0, page.hotspots().size());
    assertNull(page.nextPageToken());
  }

  @Test
  void pagesByOffsetToken() {
    when(hotspotForecastRepository.findAllOrderedByScoreDesc())
        .thenReturn(
            List.of(
                record("a", "0.90"),
                record("b", "0.80"),
                record("c", "0.70")));

    HotspotsPageResource first = hotspotForecastService.listHotspots(2, null);
    assertEquals(2, first.hotspots().size());
    assertEquals("a", first.hotspots().getFirst().forecastId());
    assertEquals("2", first.nextPageToken());

    HotspotsPageResource second = hotspotForecastService.listHotspots(2, "2");
    assertEquals(1, second.hotspots().size());
    assertEquals("c", second.hotspots().getFirst().forecastId());
    assertNull(second.nextPageToken());
  }

  private static HotspotForecastRecord record(String id, String score) {
    return new HotspotForecastRecord(
        id,
        "42963000000035012",
        "Theft",
        "2025-12",
        new BigDecimal(score),
        null,
        "RUN-TEST",
        Instant.parse("2026-07-24T00:00:00Z"));
  }
}
