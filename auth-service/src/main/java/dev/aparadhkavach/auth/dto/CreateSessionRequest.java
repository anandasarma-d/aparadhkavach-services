package dev.aparadhkavach.auth.dto;

/**
 * Session mint body.
 *
 * <ul>
 *   <li>Production: {@code catalystUserId} from Embedded Auth — Auth Service looks up the user via
 *       Catalyst User Management and mints from the <em>server</em> role (ignores client {@code
 *       role}).
 *   <li>Bootstrap: when {@code AUTH_ALLOW_DEV_MINT=true}, {@code role} (+ optional sub/displayName).
 * </ul>
 */
public record CreateSessionRequest(
    String catalystUserId,
    String catalystAccessToken,
    String email,
    String role,
    String sub,
    String displayName) {}
