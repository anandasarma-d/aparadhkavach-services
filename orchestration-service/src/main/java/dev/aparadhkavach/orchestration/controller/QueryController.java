package dev.aparadhkavach.orchestration.controller;

import dev.aparadhkavach.orchestration.query.service.QueryService;
import dev.aparadhkavach.orchestration.dto.QueryRequest;
import dev.aparadhkavach.orchestration.dto.QueryResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single-shot citation-backed Q&amp;A (F3 / mvp2/11). Path locked to {@code POST /v1/queries:ask}
 * — no conversation store.
 */
@RestController
public class QueryController {

  private static final Logger log = LoggerFactory.getLogger(QueryController.class);

  private final QueryService queryService;

  public QueryController(QueryService queryService) {
    this.queryService = queryService;
  }

  @PostMapping("/v1/queries:ask")
  public QueryResource ask(@RequestBody QueryRequest request) {
    log.info(
        "ask request accusedId={} firId={}",
        request == null ? null : request.accusedId(),
        request == null ? null : request.firId());
    return queryService.ask(request);
  }
}
