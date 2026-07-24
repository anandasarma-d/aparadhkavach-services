package dev.aparadhkavach.commons.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Catalyst SDK Self Client OAuth credentials — needed only for local/external Spring Boot access to
 * Catalyst DataStore/Authentication outside a deployed AppSail context (External Services —
 * Provisioning & Account Setup Guide, "Catalyst SDK Access from Local/External Code"). Inside a
 * real AppSail deployment the SDK auto-initializes from the execution context and these values go
 * unused.
 *
 * <p>Shared here (rather than per-service) because every service that talks to Catalyst DataStore
 * binds the identical five fields.
 */
@Component
@ConfigurationProperties(prefix = "catalyst")
public class CatalystProperties {

  private String projectId;
  private String zaid;
  private String clientId;
  private String clientSecret;
  private String refreshToken;
  private String environment;

  /** Catalyst API host for Self Client / third-party SDK init (ADR-021). */
  private String projectDomain = "https://api.catalyst.zoho.com";

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getZaid() {
    return zaid;
  }

  public void setZaid(String zaid) {
    this.zaid = zaid;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public String getProjectDomain() {
    return projectDomain;
  }

  public void setProjectDomain(String projectDomain) {
    this.projectDomain = projectDomain;
  }
}
