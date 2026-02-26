/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.annotations.TypeName;

public sealed interface TeamEvent {

  @TypeName("akka-team-created")
  record TeamCreated(String teamId, String taskListId) implements TeamEvent {}

  @TypeName("akka-team-member-added")
  record MemberAdded(String agentId, String agentType, String description) implements TeamEvent {}

  @TypeName("akka-team-member-removed")
  record MemberRemoved(String agentId) implements TeamEvent {}

  @TypeName("akka-team-disbanded")
  record TeamDisbanded() implements TeamEvent {}
}
