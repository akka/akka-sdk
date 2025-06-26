/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Number {

  public final int value;

  @JsonCreator
  public Number(@JsonProperty("value") int value) {
    this.value = value;
  }
}
