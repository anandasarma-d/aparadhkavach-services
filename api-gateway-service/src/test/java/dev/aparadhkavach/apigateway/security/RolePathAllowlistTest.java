package dev.aparadhkavach.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RolePathAllowlistTest {

  private final RolePathAllowlist allowlist = new RolePathAllowlist();

  @Test
  void investigatorMayAsk() {
    assertThat(allowlist.isAllowed("INVESTIGATOR", "/v1/queries:ask")).isTrue();
  }

  @Test
  void supervisorMayAsk() {
    assertThat(allowlist.isAllowed("SUPERVISOR", "/v1/queries:ask")).isTrue();
  }

  @Test
  void analystMayNotAsk() {
    assertThat(allowlist.isAllowed("ANALYST", "/v1/queries:ask")).isFalse();
  }

  @Test
  void policymakerMayNotAsk() {
    assertThat(allowlist.isAllowed("POLICYMAKER", "/v1/queries:ask")).isFalse();
  }
}
