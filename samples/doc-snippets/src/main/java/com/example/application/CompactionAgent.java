package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

import java.util.stream.Collectors;

// tag::compaction[]
@ComponentId("compaction-agent")
public class CompactionAgent extends Agent {
  private static final String SYSTEM_MESSAGE =
      """
      You can compact an interaction history with an LLM. From the given
      USER, TOOL_CALL_RESPONSE and AI messages you create one single user message and one single
      ai message.
      
      The interaction history starts with USER: followed by the user message.
      For each user message there is a corresponding response for AI that starts with AI:
      Keep the original style of user question and AI answer in the summary.
      
      Note that AI messages may contain TOOL_CALL_REQUEST(S) and be followed by TOOL_CALL_RESPONSE(S).
      Make sure to keep this information in the generated ai message.
      Do not keep it as structured tool calls, but make sure to extract the relevant context.
      
      Your response should follow a strict json schema as defined bellow.
      {
        "userMessage": "<the user message summary>",
        "aiMessage: "<the AI message summary>",
      }
      
      Do not include any explanations or text outside of the JSON structure.
      """.stripIndent(); // <1>

  public record Result(String userMessage, String aiMessage) {
  }

  private final ComponentClient componentClient;

  public CompactionAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Result> summarizeSessionHistory(SessionHistory history) { // <2>
    String concatenatedMessages =
        history.messages().stream().map(msg -> {
              return switch (msg) {
                case SessionMessage.UserMessage userMsg -> "\n\nUSER:\n" + userMsg.text(); // <3>

                case SessionMessage.AiMessage aiMessage -> {
                  var aiText = "\n\nAI:\n" + aiMessage.text();
                  yield aiMessage.toolCallRequests()
                    .stream()
                    .reduce(
                      aiText,
                      // if there are tool requests, also append them to the aiText
                      (acc, req) -> acc +
                        "\n\tTOOL_CALL_REQUEST: id=" + req.id() + ", name=" + req.name() + ", args=" + req.arguments() + " \n",
                      String::concat);
                }

                case SessionMessage.ToolCallResponse toolRes -> "\n\nTOOL_CALL_RESPONSE:\n" + toolRes.text();
              };
            })
            .collect(Collectors.joining()); // <3>

    return effects()
        .memory(MemoryProvider.none()) // <4>
        .model(ModelProvider
            .openAi()
            .withModelName("gpt-4o-mini")
            .withApiKey(System.getenv("OPENAI_API_KEY"))
            .withMaxTokens(1000))
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(concatenatedMessages)
        .responseAs(Result.class)

      .thenReply();
  }
}
// end::compaction[]


