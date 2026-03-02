/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akkajavasdk.protocol.SerializationTestProtos.SimpleMessage;

@Component(id = "protobuf-agent-conforms-to")
public class ProtobufAgentWithConformsTo extends Agent {

  // string input → protobuf output with schema (responseConformsTo)
  public Effect<SimpleMessage> generateProtobufWithSchema(String question) {
    return effects()
        .systemMessage("Generate a response")
        .userMessage(question)
        .responseConformsTo(SimpleMessage.class)
        .thenReply();
  }
}
