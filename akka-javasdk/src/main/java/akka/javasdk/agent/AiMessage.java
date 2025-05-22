/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;


public record AiMessage(String text) implements ConversationMessage {

}
