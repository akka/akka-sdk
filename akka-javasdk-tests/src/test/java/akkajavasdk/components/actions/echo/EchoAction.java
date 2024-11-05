/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.actions.echo;

import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akkajavasdk.StaticTestBuffer;

import java.util.List;

@ComponentId("echo")
public class EchoAction extends TimedAction {

  private final ComponentClient componentClient;

  public EchoAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect stringMessage(String msg) {
    StaticTestBuffer.addValue("echo-action", msg);
    return effects().done();
  }

  public Effect stringMessages(List<String> msg) {
    StaticTestBuffer.addValue("echo-action", String.join(" ", msg));
    return effects().done();
  }

  public record SomeCommand(String text) {}
  public Effect commandMessages(List<SomeCommand> msg) {
    StaticTestBuffer.addValue("echo-action", String.join(" ", msg.stream().map(c -> c.text).toList()));
    return effects().done();
  }

}
