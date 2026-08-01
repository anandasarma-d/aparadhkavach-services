package dev.aparadhkavach.auth.catalyst;

/** Catalyst application user as returned by the Admin/AppSail User Management API. */
public record CatalystProjectUser(
    String userId,
    String email,
    String displayName,
    String roleName,
    String status,
    Boolean confirmed) {}
