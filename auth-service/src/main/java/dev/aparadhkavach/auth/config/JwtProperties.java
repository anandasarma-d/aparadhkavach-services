package dev.aparadhkavach.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aparadhkavach.jwt")
public class JwtProperties {

  /** PEM PKCS#8 private key (RSA). May use literal \\n for AppSail single-line env. */
  private String privateKey;

  /** Matching X.509 public key PEM — used to verify tokens on /me. */
  private String publicKey;

  private int expiryHours = 1;

  public String getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(String privateKey) {
    this.privateKey = privateKey;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }

  public int getExpiryHours() {
    return expiryHours;
  }

  public void setExpiryHours(int expiryHours) {
    this.expiryHours = expiryHours;
  }
}
