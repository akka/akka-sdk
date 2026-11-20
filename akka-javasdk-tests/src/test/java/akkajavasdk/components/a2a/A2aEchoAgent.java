/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;

@Component(id = "a2a-echo-agent", name = "Echo agent", description = "Repeats what it is told")
@A2aSkill(
    id = "echo",
    name = "Echo",
    description = "Repeats the text it receives",
    tags = {"echo"},
    examples = {"hello"})
public class A2aEchoAgent extends Agent {

  public Effect<String> answer(String text) {
    return effects().systemMessage("Repeat the message.").userMessage(text).thenReply();
  }
}
