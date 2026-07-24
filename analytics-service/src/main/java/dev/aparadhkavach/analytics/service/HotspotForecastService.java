package dev.aparadhkavach.analytics.service;

import dev.aparadhkavach.analytics.dto.HotspotResource;
import dev.aparadhkavach.analytics.dto.HotspotsPageResource;
import dev.aparadhkavach.analytics.model.HotspotForecastRecord;
import dev.aparadhkavach.analytics.repository.HotspotForecastRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HotspotForecastService {

  static final int DEFAULT_LIMIT = 100;
  static final int MAX_LIMIT = 500;

  private final HotspotForecastRepository hotspotForecastRepository;

  public HotspotForecastService(HotspotForecastRepository hotspotForecastRepository) {
    this.hotspotForecastRepository = hotspotForecastRepository;
  }

  /**
   * Lists hotspot forecasts. Empty table → empty {@code hotspots} (does not invent scores).
   *
   * @param limit max rows to return (clamped)
   * @param pageToken opaque offset as decimal string, or null/blank for start
   */
  public HotspotsPageResource listHotspots(Integer limit, String pageToken) {
    int pageSize = normalizeLimit(limit);
    int offset = parseOffset(pageToken);

    List<HotspotForecastRecord> all = hotspotForecastRepository.findAllOrderedByScoreDesc();
    if (offset >= all.size()) {
      return new HotspotsPageResource(List.of(), null);
    }

    int end = Math.min(offset + pageSize, all.size());
    List<HotspotResource> page =
        all.subList(offset, end).stream().map(HotspotForecastService::toResource).toList();
    String next = end < all.size() ? Integer.toString(end) : null;
    return new HotspotsPageResource(page, next);
  }

  private static HotspotResource toResource(HotspotForecastRecord record) {
    return new HotspotResource(
        record.forecastId(),
        record.districtId(),
        record.crimeType(),
        record.forecastWindow(),
        record.hotspotScore(),
        record.confidence(),
        record.pipelineRunId(),
        record.forecastedAt());
  }

  private static int normalizeLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private static int parseOffset(String pageToken) {
    if (pageToken == null || pageToken.isBlank()) {
      return 0;
    }
    try {
      int offset = Integer.parseInt(pageToken.trim());
      return Math.max(offset, 0);
    } catch (NumberFormatException ex) {
      return 0;
    }
  }
}
