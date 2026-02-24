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

  // string input → protobuf output (LLM returns JSON, deserialized as protobuf via responseAs)
  public Effect<SimpleMessage> generateProtobuf(String question) {
    return effects()
        .systemMessage("Generate a response")
        .userMessage(question)
        .responseAs(SimpleMessage.class)
        .thenReply();
  }

  // string input → protobuf output with schema (responseConformsTo)
  public Effect<SimpleMessage> generateProtobufWithSchema(String question) {
    return effects()
        .systemMessage("Generate a response")
        .userMessage(question)
        .responseConformsTo(SimpleMessage.class)
        .thenReply();
  }

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
