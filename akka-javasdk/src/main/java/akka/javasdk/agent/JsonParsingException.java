/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Exception thrown when there is an error parsing JSON responses from the model.
 * This exception can be used to handle JSON parsing errors gracefully.
 *
 * It includes the raw JSON string that caused the error.
 */
public class JsonParsingException extends RuntimeException {

  private String rawJson;

  public JsonParsingException(String message, Throwable cause, String rawJson) {
    super(message, cause);
    this.rawJson = rawJson;
  }

  public String getRawJson() {
    return rawJson;
  }
}
