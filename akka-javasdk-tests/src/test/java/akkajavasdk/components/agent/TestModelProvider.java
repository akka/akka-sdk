/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.japi.Pair;
import akka.javasdk.agent.ModelProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class TestModelProvider implements ModelProvider.Custom {

  private List<Pair<Predicate<String>, String>> responsePredicates = new ArrayList<>();


  @Override
  public Object createChatModel() {
    return new ChatModel() {
      @Override
      public ChatResponse chat(ChatRequest chatRequest) {

        var lastUserMessageText = Optional.ofNullable(chatRequest.messages().getLast())
          .filter(chatMessage -> chatMessage instanceof UserMessage)
          .map(userMessage -> (UserMessage) userMessage)
          .map(UserMessage::singleText)
          .orElseThrow(() -> new RuntimeException("No user message found"));

        var textResponse = responsePredicates.stream()
          .filter(pair -> pair.first().test(lastUserMessageText))
          .findFirst()
          .map(Pair::second)
          .orElseThrow();

        return chatResponse(textResponse);
      }

      private ChatResponse chatResponse(String response) {
        return new ChatResponse.Builder()
          .modelName("test-model")
          .finishReason(FinishReason.STOP)
          .aiMessage(new AiMessage(response))
          .build();
      }
    };
  }

  public void mockResponse(Predicate<String> predicate, String response) {
    responsePredicates.add(new Pair<>(predicate, response));
  }

  public void reset() {
    responsePredicates = new ArrayList<>();
  }
}
