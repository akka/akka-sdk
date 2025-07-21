/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.CommandException;
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;
import akkajavasdk.components.MyException;

@ComponentId("some-agent-returning-errors")
public class SomeAgentReturningErrors extends Agent {

  public Effect<String> run(String errorType) {
    if ("errorMessage".equals(errorType)) {
      return effects().error(errorType);
    } else if ("errorCommandException".equals(errorType)) {
      return effects().error(new CommandException(errorType));
    } else if ("errorMyException".equals(errorType)) {
      return effects().error(new MyException(errorType, new MyException.SomeData("some data")));
    } else if ("throwMyException".equals(errorType)) {
      throw new MyException(errorType, new MyException.SomeData("some data"));
    } else if ("throwRuntimeException".equals(errorType)) {
      throw new RuntimeException(errorType);
    } else {
      return effects().reply("No error triggered for: " + errorType);
    }
  }
}
