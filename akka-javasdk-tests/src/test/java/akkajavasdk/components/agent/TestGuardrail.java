/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.TextGuardrail;

public class TestGuardrail implements TextGuardrail {
  private final String searchFor;

  public TestGuardrail(GuardrailContext context) {
    this.searchFor = context.config().getString("search-for");
  }

  @Override
  public Result evaluate(String text) {
    if (text.contains(searchFor)) {
      return new Result(false, "Don't say: " + searchFor);
    } else {
      return new Result(true, "");
    }
  }
}
