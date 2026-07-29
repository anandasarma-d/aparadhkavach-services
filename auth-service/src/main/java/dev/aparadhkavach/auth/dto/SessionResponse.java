package dev.aparadhkavach.auth.dto;

import java.util.List;

public record SessionResponse(
    String accessToken, String role, String displayName, List<String> views, String homeView) {}
