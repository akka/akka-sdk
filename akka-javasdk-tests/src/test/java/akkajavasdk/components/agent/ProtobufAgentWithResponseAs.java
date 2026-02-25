/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akkajavasdk.protocol.SerializationTestProtos.SimpleMessage;

@Component(id = "protobuf-agent-response-as")
public class ProtobufAgentWithResponseAs extends Agent {

  // string input → protobuf output (LLM returns JSON, deserialized as protobuf via responseAs)
  public Effect<SimpleMessage> generateProtobuf(String question) {
    return effects()
        .systemMessage("Generate a response")
        .userMessage(question)
        .responseAs(SimpleMessage.class)
        .thenReply();
  }
}
