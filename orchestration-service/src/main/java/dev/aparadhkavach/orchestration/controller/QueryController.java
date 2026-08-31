package dev.aparadhkavach.orchestration.controller;

import dev.aparadhkavach.orchestration.dto.QueryRequest;
import dev.aparadhkavach.orchestration.dto.QueryResource;
import dev.aparadhkavach.orchestration.dto.RecordsSearchRequest;
import dev.aparadhkavach.orchestration.query.service.ConversationService;
import dev.aparadhkavach.orchestration.query.service.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Citation-backed Q&amp;A (F3 / mvp2/11) plus mvp2/20 plain-English records discovery. Paths
 * locked to colon forms under {@code /v1/queries}.
 */
@RestController
public class QueryController {

  private static final Logger log = LoggerFactory.getLogger(QueryController.class);

  private final ConversationService conversationService;
  private final QueryService queryService;

  public QueryController(ConversationService conversationService, QueryService queryService) {
    this.conversationService = conversationService;
    this.queryService = queryService;
  }

  @PostMapping("/v1/queries:ask")
  public QueryResource ask(@RequestBody QueryRequest request) {
    log.info(
        "ask request accusedId={} firId={} conversationId={} followUp={}",
        request == null ? null : request.accusedId(),
        request == null ? null : request.firId(),
        request == null ? null : request.conversationId(),
        request == null || request.followUp() == null || request.followUp().isBlank()
            ? null
            : "yes");
    String conversationId = request == null ? null : request.conversationId();
    return conversationService.ask(conversationId, request);
  }

  /** mvp2/20 — NL → ANN → Claude (same envelope as ask; no ACC-/FIR- seed). */
  @PostMapping("/v1/queries:searchRecords")
  public QueryResource searchRecords(@RequestBody RecordsSearchRequest request) {
    String q = request == null ? null : request.q();
    log.info(
        "searchRecords qChars={} conversationId={} limit={}",
        q == null ? 0 : q.trim().length(),
        request == null ? null : request.conversationId(),
        request == null ? null : request.limit());
    return queryService.searchRecords(
        q,
        request == null ? null : request.conversationId(),
        request == null ? null : request.limit());
  }
}
