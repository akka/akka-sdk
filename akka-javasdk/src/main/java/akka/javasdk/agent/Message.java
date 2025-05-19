/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserMessage.class, name = "UM"),
    @JsonSubTypes.Type(value = AiMessage.class, name = "AIM")})
public interface Message {

}



///**
// * Record representing a single message in the conversation history.
// *
// * @param content The text content of the message
// * @param type The type of the message (AI, USER, or TOOL)
// */
///*record Message(String content, MessageType type) {
//  /**
//   * Creates a new message with USER type.
//   *
//   * @param content The text content of the user message
//   * @return A new Message instance with USER type
//   */
//  public static Message fromUser(String content) {
//    return new Message(content, MessageType.USER);
//  }
//
//  /**
//   * Creates a new message with TOOL type.
//   *
//   * @param content The text content of the tool message
//   * @return A new Message instance with TOOL type
//   */
//  public static Message fromTool(String content) {
//    return new Message(content, MessageType.TOOL);
//  }
//
//  /**
//   * Creates a new message with AI type.
//   *
//   * @param content The text content of the AI message
//   * @return A new Message instance with AI type
//   */
//  public static Message fromAi(String content) {
//    return new Message(content, MessageType.AI);
//  }
//}*/
