package demo.multiagent.domain;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

public class MessageAdapter {

  public static dev.langchain4j.data.message.ChatMessage toLangchain4jChatMessage(SessionMessage sessionMessage) {
    return switch (sessionMessage.type()) {
      case AI -> new AiMessage(sessionMessage.content());
      case USER -> new UserMessage(sessionMessage.content());
    };
  }
}
