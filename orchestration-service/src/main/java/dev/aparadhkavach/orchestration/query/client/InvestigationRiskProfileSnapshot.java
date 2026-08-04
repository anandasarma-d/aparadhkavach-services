package dev.aparadhkavach.orchestration.query.client;

import java.math.BigDecimal;

/** Minimal Investigation risk-profile fields used as Claude context (mvp2/11). */
public record InvestigationRiskProfileSnapshot(
    String accusedId,
    String name,
    String addressDistrictId,
    Integer priorOffenseCount,
    BigDecimal riskScore) {}
