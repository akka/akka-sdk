/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Done {

  public static Done instance = new Done("done");
  public final String message;

  @JsonCreator
  public Done(@JsonProperty("message") String message) {
    this.message = message;
  }
}
