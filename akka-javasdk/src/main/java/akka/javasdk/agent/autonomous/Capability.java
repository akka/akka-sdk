/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A capability that can be added to an autonomous agent's strategy.
 *
 * <p>Each capability adds coordination tools and behaviour to the agent. Current capabilities:
 *
 * <ul>
 *   <li>{@link DelegationCapability} — delegate subtasks to other autonomous agents
 *   <li>{@link HandoffCapability} — hand off the current task to another autonomous agent
 *   <li>{@link TeamCapability} — form and manage teams of autonomous agents
 *   <li>{@link TaskListCapability} — interact with a shared task list
 *   <li>{@link MessageCapability} — send and receive messages with team members
 *   <li>{@link ExternalInputCapability} — request external input (approval, clarification, etc.)
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DelegationCapability.class, name = "delegation"),
  @JsonSubTypes.Type(value = HandoffCapability.class, name = "handoff"),
  @JsonSubTypes.Type(value = TeamCapability.class, name = "team"),
  @JsonSubTypes.Type(value = TaskListCapability.class, name = "taskList"),
  @JsonSubTypes.Type(value = MessageCapability.class, name = "message"),
  @JsonSubTypes.Type(value = ExternalInputCapability.class, name = "externalInput")
})
public sealed interface Capability
    permits DelegationCapability,
        HandoffCapability,
        TeamCapability,
        TaskListCapability,
        MessageCapability,
        ExternalInputCapability {}
