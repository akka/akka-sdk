/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akkajavasdk.protocol.SerializationTestProtos.SimpleMessage;

@Component(id = "protobuf-agent")
public class ProtobufAgent extends Agent {

  // protobuf input → Java record output (via LLM)
  public Effect<SomeAgent.SomeResponse> processProtobuf(SimpleMessage input) {
    return effects()
        .systemMessage("Echo the input")
        .userMessage(input.getText())
        .map(SomeAgent.SomeResponse::new)
        .thenReply();
  }
}
