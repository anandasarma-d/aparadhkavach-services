package dev.aparadhkavach.commons.catalyst;

import com.catalyst.config.ZCThreadLocal;
import com.zc.api.APIConstants.ZCUserScope;
import com.zc.auth.ZCAuth;
import com.zc.common.ZCProject;
import com.zc.common.ZCProjectConfig;
import com.zc.component.USER_TYPE;
import dev.aparadhkavach.commons.config.CatalystProperties;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicReference;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Initializes the Catalyst Java SDK for local/external Self Client access (External Services
 * guide). Inside AppSail the request filter initializes per-request credentials instead; this bean
 * still holds the optional {@link ZCProject} when Self Client init succeeds.
 */
@Component
public class CatalystSdkInitializer {

  private static final Logger log = LoggerFactory.getLogger(CatalystSdkInitializer.class);
  private static final String LOCAL_PLACEHOLDER = "local-dev-placeholder";

  private final CatalystProperties properties;
  private final AtomicReference<ZCProject> project = new AtomicReference<>();

  public CatalystSdkInitializer(CatalystProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void initSelfClientIfConfigured() {
    if (!hasRealSelfClientCredentials()) {
      log.info(
          "Catalyst Self Client credentials look like local placeholders — skipping startup "
              + "ZCProject init (AppSail request-scoped init still applies when deployed)");
      return;
    }
    try {
      ZCThreadLocal.putValue("user_type", USER_TYPE.ADMIN);
      JSONObject oAuthParams = new JSONObject();
      oAuthParams.put("client_id", properties.getClientId());
      oAuthParams.put("client_secret", properties.getClientSecret());
      oAuthParams.put("refresh_token", properties.getRefreshToken());
      oAuthParams.put("grant_type", "refresh_token");

      ZCAuth auth = ZCAuth.getInstance(oAuthParams);
      auth.setScope(ZCUserScope.ADMIN);

      ZCProjectConfig config =
          ZCProjectConfig.newBuilder()
              .setProjectId(Long.parseLong(properties.getProjectId()))
              .setProjectKey(properties.getZaid())
              .setZcAuth(auth)
              .setProjectDomain(properties.getProjectDomain())
              .setEnvironment(properties.getEnvironment())
              .build();

      project.set(ZCProject.initProject(config, ""));
      log.info("Catalyst Self Client ZCProject initialized for DataStore/ZCQL access");
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to initialize Catalyst Self Client SDK", ex);
    }
  }

  public ZCProject projectOrNull() {
    return project.get();
  }

  private boolean hasRealSelfClientCredentials() {
    return isConfigured(properties.getProjectId())
        && isConfigured(properties.getZaid())
        && isConfigured(properties.getClientId())
        && isConfigured(properties.getClientSecret())
        && isConfigured(properties.getRefreshToken())
        && isConfigured(properties.getProjectDomain())
        && isConfigured(properties.getEnvironment());
  }

  private static boolean isConfigured(String value) {
    return value != null && !value.isBlank() && !value.startsWith(LOCAL_PLACEHOLDER);
  }
}
