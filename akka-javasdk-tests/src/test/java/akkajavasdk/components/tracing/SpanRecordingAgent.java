/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracing;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "span-recording-agent")
public class SpanRecordingAgent extends Agent {

  public Effect<String> query(String question) {
    return effects().systemMessage("You are a helpful...").userMessage(question).thenReply();
  }

  @FunctionTool(description = "Records the current span")
  private String recordSpan() {
    SpanRecorder.record("agent-tool");
    return "recorded";
  }
}
