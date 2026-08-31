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
  void investigatorMaySearchRecords() {
    assertThat(allowlist.isAllowed("INVESTIGATOR", "/v1/queries:searchRecords")).isTrue();
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
  void analystMayNotSearchRecords() {
    assertThat(allowlist.isAllowed("ANALYST", "/v1/queries:searchRecords")).isFalse();
  }

  @Test
  void investigatorMayUseConversations() {
    assertThat(allowlist.isAllowed("INVESTIGATOR", "/v1/conversations")).isTrue();
    assertThat(allowlist.isAllowed("INVESTIGATOR", "/v1/conversations/abc/queries")).isTrue();
    assertThat(allowlist.isAllowed("INVESTIGATOR", "/v1/conversations/abc/queries:voice")).isTrue();
  }

  @Test
  void analystMayNotUseConversations() {
    assertThat(allowlist.isAllowed("ANALYST", "/v1/conversations")).isFalse();
  }
}
