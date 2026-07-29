package dev.aparadhkavach.auth.jwt;

import dev.aparadhkavach.auth.config.JwtProperties;
import dev.aparadhkavach.auth.matrix.AppRole;
import dev.aparadhkavach.auth.matrix.CapabilityMatrix;
import io.jsonwebtoken.Jwts;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JwtIssuer {

  private final JwtProperties jwtProperties;
  private final CapabilityMatrix capabilityMatrix;
  private volatile RSAPrivateKey privateKey;

  public JwtIssuer(JwtProperties jwtProperties, CapabilityMatrix capabilityMatrix) {
    this.jwtProperties = jwtProperties;
    this.capabilityMatrix = capabilityMatrix;
  }

  public IssuedToken mint(String sub, AppRole role, String displayName) {
    List<String> views = capabilityMatrix.viewsFor(role);
    String homeView = capabilityMatrix.homeViewFor(role);
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(Math.max(1, jwtProperties.getExpiryHours()) * 3600L);

    String token =
        Jwts.builder()
            .subject(sub)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claim("role", role.name())
            .claim("displayName", displayName)
            .claim("views", views)
            .claim("homeView", homeView)
            .signWith(privateKey(), Jwts.SIG.RS256)
            .compact();

    return new IssuedToken(token, role.name(), displayName, views, homeView);
  }

  private RSAPrivateKey privateKey() {
    RSAPrivateKey local = privateKey;
    if (local == null) {
      synchronized (this) {
        local = privateKey;
        if (local == null) {
          local = PemKeyReader.readPrivateKey(jwtProperties.getPrivateKey());
          privateKey = local;
        }
      }
    }
    return local;
  }

  public record IssuedToken(
      String accessToken, String role, String displayName, List<String> views, String homeView) {}
}
