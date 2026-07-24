package dev.aparadhkavach.commons.risk;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Section 7.5.1: surface the top N features by absolute importance; ties broken by declaration
 * order in {@link RiskFeature}.
 */
public final class TopFeatureImportanceSelector {
  private TopFeatureImportanceSelector() {}

  public static Map<String, Double> selectTop(Map<String, Double> featureImportance, int topN) {
    Objects.requireNonNull(featureImportance, "featureImportance");
    if (topN < 1) {
      throw new IllegalArgumentException("topN must be >= 1");
    }

    return featureImportance.entrySet().stream()
        .sorted(
            Comparator.<Map.Entry<String, Double>>comparingDouble(
                    e -> Math.abs(e.getValue() == null ? 0.0 : e.getValue()))
                .reversed()
                .thenComparingInt(e -> RiskFeature.declarationOrder(e.getKey())))
        .limit(topN)
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
  }
}
