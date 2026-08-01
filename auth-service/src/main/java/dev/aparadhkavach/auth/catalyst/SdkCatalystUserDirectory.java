package dev.aparadhkavach.auth.catalyst;

import com.zc.common.ZCProject;
import com.zc.component.ZCRoleDetails;
import com.zc.component.ZCUserDetail;
import com.zc.component.users.ZCUser;
import dev.aparadhkavach.commons.catalyst.CatalystSdkInitializer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves Catalyst users via the Java SDK.
 *
 * <p>On AppSail, {@link CatalystSdkInitializer} may be uninitialized (placeholders); {@link
 * ZCUser#getInstance()} still works after the commons AppSail request filter bridges platform
 * headers. Locally, Self Client credentials produce a {@link ZCProject} used instead.
 */
@Component
public class SdkCatalystUserDirectory implements CatalystUserDirectory {

  private final CatalystSdkInitializer initializer;

  public SdkCatalystUserDirectory(CatalystSdkInitializer initializer) {
    this.initializer = initializer;
  }

  @Override
  public CatalystProjectUser findByUserId(long userId) throws Exception {
    ZCProject project = initializer.projectOrNull();
    ZCUser users = project == null ? ZCUser.getInstance() : ZCUser.getInstance(project);
    ZCUserDetail detail = users.getUser(userId);
    if (detail == null) {
      return null;
    }

    String first = detail.getFirstName() != null ? detail.getFirstName().trim() : "";
    String last = detail.getLastName() != null ? detail.getLastName().trim() : "";
    String displayName = (first + " " + last).trim();
    if (!StringUtils.hasText(displayName)) {
      displayName = detail.getEmailId() != null ? detail.getEmailId() : String.valueOf(userId);
    }

    String roleName = null;
    ZCRoleDetails roleDetails = detail.getRoleDetails();
    if (roleDetails != null && StringUtils.hasText(roleDetails.getName())) {
      roleName = roleDetails.getName().trim();
    }

    Long id = detail.getUserId();
    return new CatalystProjectUser(
        id != null ? String.valueOf(id) : String.valueOf(userId),
        detail.getEmailId(),
        displayName,
        roleName,
        detail.getStatus(),
        detail.getIsConfirmed());
  }
}
