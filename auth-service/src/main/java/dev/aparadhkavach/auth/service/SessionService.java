package dev.aparadhkavach.auth.service;

import dev.aparadhkavach.auth.catalyst.CatalystProjectUser;
import dev.aparadhkavach.auth.catalyst.CatalystUserDirectory;
import dev.aparadhkavach.auth.config.AuthProperties;
import dev.aparadhkavach.auth.config.JwtProperties;
import dev.aparadhkavach.auth.dto.CreateSessionRequest;
import dev.aparadhkavach.auth.dto.SessionResponse;
import dev.aparadhkavach.auth.jwt.JwtIssuer;
import dev.aparadhkavach.auth.jwt.PublicPemReader;
import dev.aparadhkavach.auth.matrix.AppRole;
import dev.aparadhkavach.commons.exception.UnauthorizedException;
import dev.aparadhkavach.commons.exception.ValidationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionService {

  private static final Logger log = LoggerFactory.getLogger(SessionService.class);

  private final AuthProperties authProperties;
  private final JwtProperties jwtProperties;
  private final JwtIssuer jwtIssuer;
  private final CatalystUserDirectory catalystUserDirectory;
  private volatile RSAPublicKey publicKey;

  public SessionService(
      AuthProperties authProperties,
      JwtProperties jwtProperties,
      JwtIssuer jwtIssuer,
      CatalystUserDirectory catalystUserDirectory) {
    this.authProperties = authProperties;
    this.jwtProperties = jwtProperties;
    this.jwtIssuer = jwtIssuer;
    this.catalystUserDirectory = catalystUserDirectory;
  }

  public SessionResponse create(CreateSessionRequest request) {
    if (request == null) {
      throw new ValidationException("Request body is required");
    }

    String catalystUserId = resolveCatalystUserId(request);
    if (StringUtils.hasText(catalystUserId)) {
      return mintFromCatalystUser(catalystUserId.trim(), request.email());
    }

    if (StringUtils.hasText(request.catalystAccessToken())) {
      throw new ValidationException(
          "catalystAccessToken exchange is not supported; send catalystUserId from Embedded Auth");
    }

    if (!authProperties.isAllowDevMint()) {
      throw new UnauthorizedException(
          "Session mint requires catalystUserId (or AUTH_ALLOW_DEV_MINT=true for bootstrap)");
    }

    if (!StringUtils.hasText(request.role())) {
      throw new ValidationException("role is required when using AUTH_ALLOW_DEV_MINT");
    }

    AppRole role = parseAppRole(request.role());
    String sub =
        StringUtils.hasText(request.sub())
            ? request.sub().trim()
            : "dev-" + role.name().toLowerCase(Locale.ROOT);
    String displayName =
        StringUtils.hasText(request.displayName())
            ? request.displayName().trim()
            : Character.toUpperCase(role.name().charAt(0))
                + role.name().substring(1).toLowerCase(Locale.ROOT);

    return toResponse(jwtIssuer.mint(sub, role, displayName));
  }

  public SessionResponse me(String bearerToken) {
    Claims claims = parseClaims(bearerToken);
    @SuppressWarnings("unchecked")
    List<String> views = claims.get("views", List.class);
    return new SessionResponse(
        null,
        claims.get("role", String.class),
        claims.get("displayName", String.class),
        views,
        claims.get("homeView", String.class));
  }

  public void revoke(String bearerToken) {
    parseClaims(bearerToken);
  }

  private SessionResponse mintFromCatalystUser(String catalystUserId, String claimedEmail) {
    long t0 = System.nanoTime();
    long userId;
    try {
      userId = Long.parseLong(catalystUserId);
    } catch (NumberFormatException ex) {
      throw new ValidationException("catalystUserId must be numeric");
    }

    CatalystProjectUser user;
    try {
      user = catalystUserDirectory.findByUserId(userId);
      log.info(
          "Catalyst user lookup ok userId={} tookMs={}",
          userId,
          (System.nanoTime() - t0) / 1_000_000L);
    } catch (Exception ex) {
      log.warn(
          "Catalyst user lookup failed for userId={} tookMs={}: {}",
          userId,
          (System.nanoTime() - t0) / 1_000_000L,
          ex.toString());
      throw new UnauthorizedException("Could not verify Catalyst user with Auth Service");
    }
    if (user == null) {
      throw new UnauthorizedException("Catalyst user not found");
    }

    if (StringUtils.hasText(user.status())
        && !"ACTIVE".equalsIgnoreCase(user.status().trim())) {
      throw new UnauthorizedException("Catalyst user is not ACTIVE");
    }
    if (Boolean.FALSE.equals(user.confirmed())) {
      throw new UnauthorizedException("Catalyst user has not confirmed password yet");
    }

    if (StringUtils.hasText(claimedEmail)
        && StringUtils.hasText(user.email())
        && !claimedEmail.trim().equalsIgnoreCase(user.email().trim())) {
      throw new UnauthorizedException("Catalyst user email mismatch");
    }

    AppRole role;
    try {
      role = parseAppRole(normalizeRoleName(user.roleName()));
    } catch (ValidationException ex) {
      throw new ValidationException(
          "Catalyst role \""
              + String.valueOf(user.roleName())
              + "\" cannot sign in. Set role to INVESTIGATOR / ANALYST / SUPERVISOR / POLICYMAKER.");
    }

    String displayName =
        StringUtils.hasText(user.displayName()) ? user.displayName() : role.name();
    return toResponse(jwtIssuer.mint(user.userId(), role, displayName));
  }

  /** Prefer explicit catalystUserId; accept numeric legacy catalystAccessToken as user id. */
  private static String resolveCatalystUserId(CreateSessionRequest request) {
    if (StringUtils.hasText(request.catalystUserId())) {
      return request.catalystUserId();
    }
    String token = request.catalystAccessToken();
    if (StringUtils.hasText(token) && token.trim().chars().allMatch(Character::isDigit)) {
      return token.trim();
    }
    return null;
  }

  private static String normalizeRoleName(String raw) {
    if (!StringUtils.hasText(raw)) {
      return "";
    }
    return raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
  }

  private static AppRole parseAppRole(String raw) {
    try {
      return AppRole.fromString(raw);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unknown role: " + raw);
    }
  }

  private Claims parseClaims(String bearerToken) {
    if (!StringUtils.hasText(bearerToken)) {
      throw new UnauthorizedException("Missing Bearer token");
    }
    String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken;
    try {
      return Jwts.parser().verifyWith(publicKey()).build().parseSignedClaims(token).getPayload();
    } catch (UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      throw new UnauthorizedException("Invalid or expired token");
    }
  }

  private RSAPublicKey publicKey() {
    RSAPublicKey local = publicKey;
    if (local == null) {
      synchronized (this) {
        local = publicKey;
        if (local == null) {
          local = PublicPemReader.readPublicKey(jwtProperties.getPublicKey());
          publicKey = local;
        }
      }
    }
    return local;
  }

  private static SessionResponse toResponse(JwtIssuer.IssuedToken issued) {
    return new SessionResponse(
        issued.accessToken(),
        issued.role(),
        issued.displayName(),
        issued.views(),
        issued.homeView());
  }
}
