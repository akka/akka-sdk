/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Message2 {

  public final String value;

  @JsonCreator
  public Message2(@JsonProperty("value") String value) {
    this.value = value;
  }
}
