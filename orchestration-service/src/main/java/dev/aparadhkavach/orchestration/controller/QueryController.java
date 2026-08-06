package dev.aparadhkavach.orchestration.controller;

import dev.aparadhkavach.orchestration.dto.QueryRequest;
import dev.aparadhkavach.orchestration.dto.QueryResource;
import dev.aparadhkavach.orchestration.query.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Citation-backed Q&amp;A (F3 / mvp2/11). Path locked to {@code POST /v1/queries:ask}. Persists
 * turns via {@link ConversationService} (mvp2/12 Step A/B); optional {@code followUp} resolves to
 * a cited ACC-/FIR-.
 */
@RestController
public class QueryController {

  private static final Logger log = LoggerFactory.getLogger(QueryController.class);

  private final ConversationService conversationService;

  public QueryController(ConversationService conversationService) {
    this.conversationService = conversationService;
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
}
