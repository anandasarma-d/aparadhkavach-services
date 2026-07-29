package dev.aparadhkavach.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aparadhkavach.jwt")
public class JwtProperties {

  /** X.509 public key PEM matching Auth Service private key (RS256). */
  private String publicKey;

  public String getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }
}
