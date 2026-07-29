package dev.aparadhkavach.auth.service;

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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionService {

  private final AuthProperties authProperties;
  private final JwtProperties jwtProperties;
  private final JwtIssuer jwtIssuer;
  private volatile RSAPublicKey publicKey;

  public SessionService(
      AuthProperties authProperties, JwtProperties jwtProperties, JwtIssuer jwtIssuer) {
    this.authProperties = authProperties;
    this.jwtProperties = jwtProperties;
    this.jwtIssuer = jwtIssuer;
  }

  public SessionResponse create(CreateSessionRequest request) {
    if (request == null) {
      throw new ValidationException("Request body is required");
    }

    if (StringUtils.hasText(request.catalystAccessToken())) {
      throw new ValidationException(
          "Catalyst Embedded exchange is not wired yet. Enable AUTH_ALLOW_DEV_MINT for bootstrap mint, or wait for Embedded integration.");
    }

    if (!authProperties.isAllowDevMint()) {
      throw new UnauthorizedException(
          "Session mint requires catalystAccessToken (or AUTH_ALLOW_DEV_MINT=true for bootstrap)");
    }

    if (!StringUtils.hasText(request.role())) {
      throw new ValidationException("role is required when using AUTH_ALLOW_DEV_MINT");
    }

    AppRole role;
    try {
      role = AppRole.fromString(request.role());
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unknown role: " + request.role());
    }

    String sub =
        StringUtils.hasText(request.sub())
            ? request.sub().trim()
            : "dev-" + role.name().toLowerCase();
    String displayName =
        StringUtils.hasText(request.displayName())
            ? request.displayName().trim()
            : Character.toUpperCase(role.name().charAt(0))
                + role.name().substring(1).toLowerCase();

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
