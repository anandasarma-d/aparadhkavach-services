package dev.aparadhkavach.auth.matrix;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Source of truth for role → views / homeView (mvp2/10). Client must not hard-code this map.
 */
@Component
public class CapabilityMatrix {

  private final Map<AppRole, List<AppView>> viewsByRole = new EnumMap<>(AppRole.class);
  private final Map<AppRole, AppView> homeByRole = new EnumMap<>(AppRole.class);

  public CapabilityMatrix() {
    viewsByRole.put(AppRole.INVESTIGATOR, List.of(AppView.risk, AppView.network, AppView.similar));
    viewsByRole.put(AppRole.ANALYST, List.of(AppView.hotspots, AppView.risk));
    viewsByRole.put(
        AppRole.SUPERVISOR,
        List.of(AppView.risk, AppView.hotspots, AppView.network, AppView.similar));
    viewsByRole.put(AppRole.POLICYMAKER, List.of(AppView.hotspots));

    homeByRole.put(AppRole.INVESTIGATOR, AppView.risk);
    homeByRole.put(AppRole.ANALYST, AppView.hotspots);
    homeByRole.put(AppRole.SUPERVISOR, AppView.risk);
    homeByRole.put(AppRole.POLICYMAKER, AppView.hotspots);
  }

  public List<String> viewsFor(AppRole role) {
    return viewsByRole.get(role).stream().map(Enum::name).toList();
  }

  public String homeViewFor(AppRole role) {
    return homeByRole.get(role).name();
  }
}
