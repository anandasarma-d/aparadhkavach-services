package dev.aparadhkavach.investigation.model;

/** Internal view of one {@code accused_persons} row after DataStore read. */
public record AccusedPersonRecord(
    String accusedId, String name, String addressDistrictId, Integer priorOffenseCount) {}
