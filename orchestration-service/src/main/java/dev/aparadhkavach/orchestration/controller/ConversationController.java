package dev.aparadhkavach.orchestration.controller;

import dev.aparadhkavach.orchestration.dto.ConversationCreatedResource;
import dev.aparadhkavach.orchestration.dto.ConversationResource;
import dev.aparadhkavach.orchestration.dto.QueryRequest;
import dev.aparadhkavach.orchestration.dto.QueryResource;
import dev.aparadhkavach.orchestration.query.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversation store API (mvp2/12 Step A). Ask still uses the same citation envelope as {@code
 * POST /v1/queries:ask}.
 */
@RestController
public class ConversationController {

  private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

  private final ConversationService conversationService;

  public ConversationController(ConversationService conversationService) {
    this.conversationService = conversationService;
  }

  @PostMapping("/v1/conversations")
  public ConversationCreatedResource create() {
    ConversationCreatedResource created = conversationService.create();
    log.info("conversation created id={}", created.conversationId());
    return created;
  }

  @GetMapping("/v1/conversations/{conversationId}")
  public ConversationResource get(@PathVariable String conversationId) {
    return conversationService.get(conversationId);
  }

  @PostMapping("/v1/conversations/{conversationId}/queries")
  public QueryResource ask(
      @PathVariable String conversationId, @RequestBody QueryRequest request) {
    log.info(
        "conversation ask id={} accusedId={} firId={}",
        conversationId,
        request == null ? null : request.accusedId(),
        request == null ? null : request.firId());
    return conversationService.ask(conversationId, request);
  }
}
