package dev.aparadhkavach.analytics.controller;

import dev.aparadhkavach.analytics.dto.HotspotsPageResource;
import dev.aparadhkavach.analytics.service.HotspotForecastService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature 2 read API — {@code GET /v1/analytics/hotspots} (Section 6.7). Gateway already proxies
 * {@code /v1/analytics/**} → Analytics.
 */
@RestController
@RequestMapping("/v1/analytics/hotspots")
public class HotspotController {

  private final HotspotForecastService hotspotForecastService;

  public HotspotController(HotspotForecastService hotspotForecastService) {
    this.hotspotForecastService = hotspotForecastService;
  }

  @GetMapping
  public HotspotsPageResource listHotspots(
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String pageToken) {
    return hotspotForecastService.listHotspots(limit, pageToken);
  }
}
