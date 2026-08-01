package dev.aparadhkavach.auth.catalyst;

/** Looks up a Catalyst project user by id (server-side exchange for session mint). */
public interface CatalystUserDirectory {

  CatalystProjectUser findByUserId(long userId) throws Exception;
}
