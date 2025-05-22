/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.List;

public record ConversationHistory(int maxSize, List<ConversationMessage> messages) { }
