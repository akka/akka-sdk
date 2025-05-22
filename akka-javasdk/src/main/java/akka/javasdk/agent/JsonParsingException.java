/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

public class JsonParsingException extends RuntimeException {

  private String rawJson;

  public JsonParsingException(String message, Throwable cause) {
    super(message, cause);
  }

  public JsonParsingException(String message, Throwable cause, String rawJson) {
    super(message, cause);
    this.rawJson = rawJson;
  }

  public String getRawJson() {
    return rawJson;
  }
}
