package dev.aparadhkavach.auth.dto;

/**
 * Session mint body.
 *
 * <ul>
 *   <li>Production: {@code catalystAccessToken} from Embedded Auth (exchange TBD).
 *   <li>Bootstrap: when {@code AUTH_ALLOW_DEV_MINT=true}, {@code role} (+ optional sub/displayName).
 * </ul>
 */
public record CreateSessionRequest(
    String catalystAccessToken, String role, String sub, String displayName) {}
