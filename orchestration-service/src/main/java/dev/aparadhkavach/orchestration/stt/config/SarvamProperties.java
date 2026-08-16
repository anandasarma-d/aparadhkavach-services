package dev.aparadhkavach.orchestration.stt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Sarvam STT microservice URL (mvp2/12 Step H / D-115). */
@Component
@ConfigurationProperties(prefix = "aparadhkavach.sarvam")
public class SarvamProperties {

  /** Base URL of aparadhkavach-stt-service (no trailing slash). */
  private String sttUrl = "http://localhost:8000";

  /**
   * Shared secret sent as {@code X-AparadhKavach-STT-Key}. Must match STT {@code
   * STT_INTERNAL_API_KEY}.
   */
  private String sttInternalKey = "";

  private int connectTimeoutMs = 3_000;
  private int readTimeoutMs = 60_000;

  public String getSttUrl() {
    return sttUrl;
  }

  public void setSttUrl(String sttUrl) {
    this.sttUrl = sttUrl;
  }

  public String getSttInternalKey() {
    return sttInternalKey;
  }

  public void setSttInternalKey(String sttInternalKey) {
    this.sttInternalKey = sttInternalKey;
  }

  public int getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public void setConnectTimeoutMs(int connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public int getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public void setReadTimeoutMs(int readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }
}
