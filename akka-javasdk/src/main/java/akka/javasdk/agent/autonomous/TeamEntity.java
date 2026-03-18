/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;

/** Team entity — holds member registry for a collaborative team. */
@Component(id = "akka-team")
public final class TeamEntity extends EventSourcedEntity<TeamState, TeamEvent> {

  private final String teamId;

  public TeamEntity(EventSourcedEntityContext context) {
    this.teamId = context.entityId();
  }

  @Override
  public TeamState emptyState() {
    return TeamState.empty();
  }

  public record CreateRequest(String taskListId) {}

  public Effect<Done> create(CreateRequest request) {
    if (!currentState().teamId().isEmpty()) {
      return effects().reply(done()); // idempotent
    }
    return effects()
        .persist(new TeamEvent.TeamCreated(teamId, request.taskListId()))
        .thenReply(__ -> done());
  }

  public record AddMemberRequest(
      String agentId, String agentType, String description, int maxMembers) {}

  public Effect<Done> addMember(AddMemberRequest request) {
    if (currentState().teamId().isEmpty()) {
      return effects().error("Team does not exist");
    }
    // Enforce max members per type
    if (request.maxMembers() > 0) {
      long currentCount =
          currentState().members().stream()
              .filter(m -> m.agentType().equals(request.agentType()))
              .count();
      if (currentCount >= request.maxMembers()) {
        return effects()
            .error(
                "Cannot add "
                    + request.agentType()
                    + ": maximum of "
                    + request.maxMembers()
                    + " already reached");
      }
    }
    return effects()
        .persist(
            new TeamEvent.MemberAdded(
                request.agentId(), request.agentType(), request.description()))
        .thenReply(__ -> done());
  }

  public Effect<Done> removeMember(String agentId) {
    if (currentState().teamId().isEmpty()) {
      return effects().error("Team does not exist");
    }
    return effects().persist(new TeamEvent.MemberRemoved(agentId)).thenReply(__ -> done());
  }

  public Effect<Done> disband() {
    if (currentState().teamId().isEmpty()) {
      return effects().error("Team does not exist");
    }
    if (currentState().disbanded()) {
      return effects().reply(done()); // idempotent
    }
    return effects().persist(new TeamEvent.TeamDisbanded()).thenReply(__ -> done());
  }

  public ReadOnlyEffect<TeamState> getState() {
    if (currentState().teamId().isEmpty()) {
      return effects().error("Team does not exist");
    }
    return effects().reply(currentState());
  }

  @Override
  public TeamState applyEvent(TeamEvent event) {
    return switch (event) {
      case TeamEvent.TeamCreated e ->
          new TeamState(e.teamId(), e.taskListId(), currentState().members(), false);
      case TeamEvent.MemberAdded e ->
          currentState().withMember(e.agentId(), e.agentType(), e.description());
      case TeamEvent.MemberRemoved e -> currentState().withoutMember(e.agentId());
      case TeamEvent.TeamDisbanded __ -> currentState().withDisbanded();
    };
  }
}
