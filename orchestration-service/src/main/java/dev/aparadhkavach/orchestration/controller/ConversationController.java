package dev.aparadhkavach.orchestration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.dto.ConversationCreatedResource;
import dev.aparadhkavach.orchestration.dto.ConversationResource;
import dev.aparadhkavach.orchestration.dto.FollowUpContext;
import dev.aparadhkavach.orchestration.dto.QueryRequest;
import dev.aparadhkavach.orchestration.dto.QueryResource;
import dev.aparadhkavach.orchestration.dto.VoiceQueryResource;
import dev.aparadhkavach.orchestration.query.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Conversation store API (mvp2/12 Step A) + voice follow-up (Step H / D-115). Ask still uses the
 * same citation envelope as {@code POST /v1/queries:ask}.
 */
@RestController
public class ConversationController {

  private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

  private final ConversationService conversationService;
  private final ObjectMapper objectMapper;

  public ConversationController(
      ConversationService conversationService, ObjectMapper objectMapper) {
    this.conversationService = conversationService;
    this.objectMapper = objectMapper;
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

  /**
   * ChatPanel voice (Design Flow 2 / Step H): multipart {@code audio} (+ optional {@code
   * followUpContext} JSON, {@code languageHint}). Transcribe → same Graph-RAC ask path as typed
   * input. Empty thread + spoken ACC-/FIR- seeds; otherwise follow-up resolution. Text remains
   * primary if STT is down.
   */
  @PostMapping(
      path = "/v1/conversations/{conversationId}/queries:voice",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public VoiceQueryResource askVoice(
      @PathVariable String conversationId,
      @RequestPart("audio") MultipartFile audio,
      @RequestParam(value = "languageHint", required = false, defaultValue = "en")
          String languageHint,
      @RequestParam(value = "followUpContext", required = false) String followUpContextJson)
      throws Exception {
    if (audio == null || audio.isEmpty()) {
      throw new ValidationException("audio part is required");
    }
    FollowUpContext ctx = null;
    if (followUpContextJson != null && !followUpContextJson.isBlank()) {
      ctx = objectMapper.readValue(followUpContextJson, FollowUpContext.class);
    }
    log.info(
        "conversation voice id={} bytes={} lang={}",
        conversationId,
        audio.getSize(),
        languageHint);
    return conversationService.askVoice(
        conversationId,
        audio.getBytes(),
        audio.getOriginalFilename(),
        languageHint,
        ctx);
  }
}
