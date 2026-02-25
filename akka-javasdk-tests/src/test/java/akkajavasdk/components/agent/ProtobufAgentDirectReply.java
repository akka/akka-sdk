/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akkajavasdk.protocol.SerializationTestProtos.SimpleMessage;

@Component(id = "protobuf-agent-direct-reply")
public class ProtobufAgentDirectReply extends Agent {

  // protobuf input → protobuf output (direct reply, no LLM)
  public Effect<SimpleMessage> echoProtobuf(SimpleMessage input) {
    return effects()
        .reply(
            SimpleMessage.newBuilder()
                .setText("Echo: " + input.getText())
                .setNumber(input.getNumber())
                .setFlag(input.getFlag())
                .build());
  }
}
