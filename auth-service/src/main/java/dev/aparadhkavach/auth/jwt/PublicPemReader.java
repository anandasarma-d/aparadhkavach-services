package dev.aparadhkavach.auth.jwt;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class PublicPemReader {

  private PublicPemReader() {}

  public static RSAPublicKey readPublicKey(String pemOrPlaceholder) {
    if (pemOrPlaceholder == null || pemOrPlaceholder.isBlank()) {
      throw new IllegalStateException("JWT public key is not set");
    }
    if (pemOrPlaceholder.contains("placeholder")) {
      throw new IllegalStateException("JWT public key is still a placeholder");
    }
    String normalized =
        pemOrPlaceholder
            .replace("\\n", "\n")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    try {
      byte[] decoded = Base64.getDecoder().decode(normalized);
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse JWT public key as X.509 PEM", e);
    }
  }
}
