package dev.aparadhkavach.commons.exception;

import dev.aparadhkavach.commons.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Neo4j traversal failures (Graph Intelligence module, Orchestration Service). */
public class GraphTraversalException extends ApiException {
  public GraphTraversalException(ErrorCode errorCode, String message) {
    super(errorCode, HttpStatus.BAD_GATEWAY, message);
  }
}
