package dev.aparadhkavach.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aparadhkavach.auth")
public class AuthProperties {

  /**
   * When true, POST /v1/auth/sessions accepts {role,sub,displayName} without catalystUserId.
   * Demo / local bootstrap only — disable once Embedded exchange is verified (D-080).
   */
  private boolean allowDevMint = false;

  public boolean isAllowDevMint() {
    return allowDevMint;
  }

  public void setAllowDevMint(boolean allowDevMint) {
    this.allowDevMint = allowDevMint;
  }
}
