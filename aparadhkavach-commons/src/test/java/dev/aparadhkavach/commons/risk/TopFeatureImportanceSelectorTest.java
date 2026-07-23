package dev.aparadhkavach.commons.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TopFeatureImportanceSelectorTest {

  @Test
  void selectsTopThreeByAbsoluteValueWithDeclarationOrderTieBreak() {
    Map<String, Double> all = new LinkedHashMap<>();
    all.put(RiskFeature.OFFENSE_COUNT.featureKey(), 0.20);
    all.put(RiskFeature.RECIDIVISM_INTERVAL_AVG.featureKey(), 0.40);
    all.put(RiskFeature.CRIME_TYPE_SEVERITY_MAX.featureKey(), 0.40);
    all.put(RiskFeature.DISTRICT_SPREAD.featureKey(), 0.10);
    all.put(RiskFeature.CO_ACCUSED_COUNT.featureKey(), 0.05);

    Map<String, Double> top = TopFeatureImportanceSelector.selectTop(all, 3);

    assertEquals(3, top.size());
    // 0.40 tie: recidivism_interval_avg declared before crime_type_severity_max
    assertEquals(
        RiskFeature.RECIDIVISM_INTERVAL_AVG.featureKey(), top.keySet().iterator().next());
    assertEquals(
        0.40, top.get(RiskFeature.RECIDIVISM_INTERVAL_AVG.featureKey()).doubleValue(), 1e-9);
    assertEquals(
        0.40, top.get(RiskFeature.CRIME_TYPE_SEVERITY_MAX.featureKey()).doubleValue(), 1e-9);
    assertEquals(0.20, top.get(RiskFeature.OFFENSE_COUNT.featureKey()).doubleValue(), 1e-9);
  }

  @Test
  void rejectsNonPositiveTopN() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TopFeatureImportanceSelector.selectTop(Map.of("a", 1.0), 0));
  }
}
