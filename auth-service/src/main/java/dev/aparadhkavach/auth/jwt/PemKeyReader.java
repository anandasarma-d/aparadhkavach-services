package dev.aparadhkavach.auth.jwt;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

final class PemKeyReader {

  private PemKeyReader() {}

  static RSAPrivateKey readPrivateKey(String pemOrPlaceholder) {
    if (pemOrPlaceholder == null || pemOrPlaceholder.isBlank()) {
      throw new IllegalStateException("JWT_PRIVATE_KEY is not set");
    }
    if (pemOrPlaceholder.contains("placeholder")) {
      throw new IllegalStateException(
          "JWT_PRIVATE_KEY is still a placeholder — set a real RSA PKCS#8 PEM");
    }
    String normalized =
        pemOrPlaceholder
            .replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    try {
      byte[] decoded = Base64.getDecoder().decode(normalized);
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse JWT_PRIVATE_KEY as RSA PKCS#8 PEM", e);
    }
  }
}
