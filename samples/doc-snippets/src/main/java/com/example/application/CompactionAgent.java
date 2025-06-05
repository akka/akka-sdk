package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// tag::compaction[]
@ComponentId("compaction-agent")
public class CompactionAgent extends Agent {
  private static final String SYSTEM_MESSAGE =
      """
      You can compact an interaction history with an LLM. From the given
      USER and AI messages you create one single user message and one single
      ai message.
      
      The interaction history starts with USER: followed by the user message.
      For each user message there is a corresponding response for AI that starts with AI:
      Keep the original style of user question and AI answer in the summary.
      
      Your response should follow a strict json schema as defined bellow.
      {
        "userMessage": "<the user message summary>",
        "userMessageTokens": "<number of tokens for the summarized userMessage>",
        "aiMessage: "<the AI message summary>",
        "aiMessageTokens": "<number of tokens for the summarized aiMessage>",
      }
      
      Do not include any explanations or text outside of the JSON structure.
      """.stripIndent(); // <1>

  public record Result(String userMessage, int userMessageTokens, String aiMessage, int aiMessageTokens) {}

  private final ComponentClient componentClient;

  public CompactionAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Result> summarizeSessionHistory() {
    var history = componentClient
        .forEventSourcedEntity(context().sessionId())
        .method(SessionMemoryEntity::getHistory) // <2>
        .invoke(new SessionMemoryEntity.GetHistoryCmd(Optional.empty()));

    String concatenatedMessages =
        history.messages().stream().map(msg -> {
              return switch (msg) {
                case SessionMessage.UserMessage userMsg -> "\n\nUSER:\n" + userMsg.text(); // <3>
                case SessionMessage.AiMessage aiMessage -> "\n\nAI:\n" + aiMessage.text();
              };
            })
            .collect(Collectors.joining()); // <3>

    return effects()
        .memory(MemoryProvider.none()) // <4>
        .model(ModelProvider
            .openAi()
            .withApiKey(System.getenv("OPENAI_API_KEY"))
            .withMaxTokens(1000))
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(concatenatedMessages)
        .responseAs(Result.class)
        .thenReply();
  }
}
// end::compaction[]


