package dev.aparadhkavach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.aparadhkavach.auth.catalyst.CatalystProjectUser;
import dev.aparadhkavach.auth.catalyst.CatalystUserDirectory;
import dev.aparadhkavach.auth.config.AuthProperties;
import dev.aparadhkavach.auth.config.JwtProperties;
import dev.aparadhkavach.auth.dto.CreateSessionRequest;
import dev.aparadhkavach.auth.dto.SessionResponse;
import dev.aparadhkavach.auth.jwt.JwtIssuer;
import dev.aparadhkavach.auth.matrix.AppRole;
import dev.aparadhkavach.auth.matrix.CapabilityMatrix;
import dev.aparadhkavach.commons.exception.UnauthorizedException;
import dev.aparadhkavach.commons.exception.ValidationException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionServiceTest {

  private SessionService sessions;
  private AuthProperties authProperties;
  private Map<Long, CatalystProjectUser> users;
  private AtomicBoolean failLookup;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair pair = gen.generateKeyPair();
    String privatePem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(pair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    String publicPem =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";

    JwtProperties jwt = new JwtProperties();
    jwt.setPrivateKey(privatePem);
    jwt.setPublicKey(publicPem);
    jwt.setExpiryHours(1);

    authProperties = new AuthProperties();
    authProperties.setAllowDevMint(false);

    users = new HashMap<>();
    failLookup = new AtomicBoolean(false);
    CatalystUserDirectory directory =
        userId -> {
          if (failLookup.get()) {
            throw new RuntimeException("sdk down");
          }
          return users.get(userId);
        };

    sessions =
        new SessionService(
            authProperties, jwt, new JwtIssuer(jwt, new CapabilityMatrix()), directory);
  }

  @Test
  void exchangeMintsFromServerRoleNotClientRole() {
    users.put(
        42L,
        new CatalystProjectUser(
            "42", "inv@example.com", "Inv User", "INVESTIGATOR", "ACTIVE", true));

    SessionResponse response =
        sessions.create(
            new CreateSessionRequest(
                "42", null, "inv@example.com", "POLICYMAKER", "spoof", "Spoof"));

    assertThat(response.role()).isEqualTo("INVESTIGATOR");
    assertThat(response.displayName()).isEqualTo("Inv User");
    assertThat(response.accessToken()).isNotBlank();
  }

  @Test
  void exchangeRejectsEmailMismatch() {
    users.put(
        42L,
        new CatalystProjectUser(
            "42", "inv@example.com", "Inv User", "INVESTIGATOR", "ACTIVE", true));

    assertThatThrownBy(
            () ->
                sessions.create(
                    new CreateSessionRequest("42", null, "other@example.com", null, null, null)))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("email mismatch");
  }

  @Test
  void exchangeRejectsAppAdminRole() {
    users.put(
        7L, new CatalystProjectUser("7", "a@x.com", "Admin", "App Administrator", "ACTIVE", true));

    assertThatThrownBy(
            () -> sessions.create(new CreateSessionRequest("7", null, null, null, null, null)))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("cannot sign in");
  }

  @Test
  void withoutUserIdRequiresDevMint() {
    assertThatThrownBy(
            () ->
                sessions.create(
                    new CreateSessionRequest(
                        null, null, null, AppRole.ANALYST.name(), null, null)))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("catalystUserId");
  }

  @Test
  void devMintStillWorksWhenEnabled() {
    authProperties.setAllowDevMint(true);
    SessionResponse response =
        sessions.create(
            new CreateSessionRequest(null, null, null, "ANALYST", "dev-1", "Demo Analyst"));
    assertThat(response.role()).isEqualTo("ANALYST");
    assertThat(response.displayName()).isEqualTo("Demo Analyst");
  }

  @Test
  void lookupFailureBecomesUnauthorized() {
    failLookup.set(true);
    assertThatThrownBy(
            () -> sessions.create(new CreateSessionRequest("9", null, null, null, null, null)))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Could not verify");
  }
}
