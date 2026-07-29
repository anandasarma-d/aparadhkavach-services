package dev.aparadhkavach.auth.controller;

import dev.aparadhkavach.auth.dto.CreateSessionRequest;
import dev.aparadhkavach.auth.dto.SessionResponse;
import dev.aparadhkavach.auth.service.SessionService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

  private final SessionService sessionService;

  public AuthController(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @PostMapping("/sessions")
  public SessionResponse createSession(@RequestBody(required = false) CreateSessionRequest request) {
    return sessionService.create(request);
  }

  @GetMapping("/me")
  public SessionResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    return sessionService.me(authorization);
  }

  @PostMapping("/sessions:revoke")
  public void revoke(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    sessionService.revoke(authorization);
  }
}
