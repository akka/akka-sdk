/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Guardrail;
import com.typesafe.config.Config;

public class TestGuardrail implements Guardrail {
  private final String searchFor;

  public TestGuardrail(Config config) {
    this.searchFor = config.getString("search-for");
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
