/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Team management tools — available when the strategy has team configured.
 *
 * <p>Provides tools for creating teams, adding members, managing a shared task list, and sending
 * messages to team members.
 */
public class TeamTools {

  private static final Logger log = LoggerFactory.getLogger(TeamTools.class);

  private final ComponentClient componentClient;
  private final List<TeamCapability> configs;
  private final String leadAgentId;

  // Set after createTeam() is called
  private String teamId;
  private String taskListId;

  public TeamTools(
      ComponentClient componentClient, List<TeamCapability> configs, String leadAgentId) {
    this.componentClient = componentClient;
    this.configs = configs;
    this.leadAgentId = leadAgentId;
    // Convention: teamId and taskListId derived from leadAgentId
    this.teamId = leadAgentId;
    this.taskListId = leadAgentId + "-tasks";
  }

  @FunctionTool(
      description =
          "Create a team and a shared task list for collaboration. Call this before adding"
              + " members or tasks.")
  public String createTeam() {
    try {
      // Create the task list entity
      componentClient.forEventSourcedEntity(taskListId).method(TaskListEntity::create).invoke();

      // Create the team entity with reference to the task list
      componentClient
          .forEventSourcedEntity(teamId)
          .method(TeamEntity::create)
          .invoke(new TeamEntity.CreateRequest(taskListId));

      // Create a message inbox for the team lead
      componentClient
          .forEventSourcedEntity(leadAgentId)
          .method(MessageInboxEntity::send)
          .invoke(
              new MessageInboxEntity.SendRequest("system", "Team created. You are the team lead."));

      log.info("Team {} created with task list {}", teamId, taskListId);
      return "Team created. Team ID: " + teamId + ". Task list ID: " + taskListId;
    } catch (Exception e) {
      log.error("Failed to create team", e);
      return "Error creating team: " + e.getMessage();
    }
  }

  @FunctionTool(
      description =
          "Add a team member. Specify the agent type (e.g. 'developer'). The member will be"
              + " started and connected to the team's task list and messaging.")
  public String addTeamMember(String agentType) {
    var config =
        configs.stream()
            .filter(c -> c.agentComponentId().equals(agentType))
            .findFirst()
            .orElse(null);

    if (config == null) {
      return "Error: Unknown agent type '"
          + agentType
          + "'. Available types: "
          + configs.stream().map(TeamCapability::agentComponentId).toList();
    }

    var memberInstanceId = config.agentComponentId() + "-" + UUID.randomUUID();

    try {
      var agentClass = Class.forName(config.agentClassName()).asSubclass(AutonomousAgent.class);
      var agentClient = componentClient.forAutonomousAgent(memberInstanceId, agentClass);

      // Inject team capabilities into the member
      var teamCapabilities = new ArrayList<Capability>();
      teamCapabilities.add(new TaskListCapability(taskListId, config.agentComponentId()));
      teamCapabilities.add(new MessageCapability(teamId));

      agentClient.startAsTeamMember(teamCapabilities);

      // Register in team entity (enforces max members constraint)
      componentClient
          .forEventSourcedEntity(teamId)
          .method(TeamEntity::addMember)
          .invoke(
              new TeamEntity.AddMemberRequest(
                  memberInstanceId,
                  config.agentComponentId(),
                  config.description(),
                  config.maxMembers()));

      log.info("Added team member {} (type: {}) to team {}", memberInstanceId, agentType, teamId);
      return "Added "
          + agentType
          + " to the team as "
          + memberInstanceId
          + ". They will start checking the task list for work.";
    } catch (Exception e) {
      log.error("Failed to add team member: {}", config.agentClassName(), e);
      return "Error adding team member: " + e.getMessage();
    }
  }

  private static final Pattern PARAM_PATTERN = Pattern.compile("\\{(\\w+)}");

  @FunctionTool(
      description =
          "Add a task to the team's shared task list. Specify the target agent type for"
              + " filtering. Provide instructions or template parameters if the agent's task has"
              + " a template.")
  public String addTask(String agentType, String instructions, Map<String, String> templateParams) {
    var config =
        configs.stream()
            .filter(c -> c.agentComponentId().equals(agentType))
            .findFirst()
            .orElse(null);

    if (config == null) {
      return "Error: Unknown agent type '"
          + agentType
          + "'. Available types: "
          + configs.stream().map(TeamCapability::agentComponentId).toList();
    }

    var taskId = UUID.randomUUID().toString();

    // Resolve task description, instructions, and result type from accepted tasks
    var taskDescription = instructions;
    String taskInstructions = null;
    String resultTypeName = null;

    var acceptedTasks = config.acceptedTasks();
    if (acceptedTasks != null && !acceptedTasks.isEmpty()) {
      var acceptedTask = acceptedTasks.getFirst();
      taskDescription = acceptedTask.description();
      resultTypeName = acceptedTask.resultTypeName();

      if (acceptedTask.instructionTemplate() != null
          && templateParams != null
          && !templateParams.isEmpty()) {
        taskInstructions = resolveTemplate(acceptedTask.instructionTemplate(), templateParams);
      } else {
        taskInstructions = instructions;
      }
    }

    try {
      // Create the underlying task entity with typed result
      componentClient
          .forEventSourcedEntity(taskId)
          .method(TaskEntity::create)
          .invoke(
              new TaskEntity.CreateRequest(
                  taskDescription,
                  taskInstructions,
                  resultTypeName != null ? resultTypeName : "",
                  List.of()));

      // Add to the shared task list with target agent type for filtering
      componentClient
          .forEventSourcedEntity(taskListId)
          .method(TaskListEntity::addTask)
          .invoke(
              new TaskListEntity.AddTaskRequest(
                  taskId,
                  taskInstructions != null ? taskInstructions : taskDescription,
                  List.of(agentType),
                  resultTypeName != null ? resultTypeName : ""));

      log.info("Added task {} for agent type {} to task list {}", taskId, agentType, taskListId);
      return "Task added to the shared list for " + agentType + ". Task ID: " + taskId;
    } catch (Exception e) {
      log.error("Failed to add task", e);
      return "Error adding task: " + e.getMessage();
    }
  }

  private String resolveTemplate(String template, Map<String, String> params) {
    var resolved = template;
    var matcher = PARAM_PATTERN.matcher(template);
    while (matcher.find()) {
      var paramName = matcher.group(1);
      var value = params.getOrDefault(paramName, "{" + paramName + "}");
      resolved = resolved.replace("{" + paramName + "}", value);
    }
    return resolved;
  }

  @FunctionTool(description = "Get the current status of the team — members and task progress.")
  public String getTeamStatus() {
    try {
      var teamState =
          componentClient.forEventSourcedEntity(teamId).method(TeamEntity::getState).invoke();

      var taskListState =
          componentClient
              .forEventSourcedEntity(taskListId)
              .method(TaskListEntity::getState)
              .invoke();

      var sb = new StringBuilder();
      sb.append("Team members (").append(teamState.members().size()).append("):");
      for (var member : teamState.members()) {
        sb.append("\n- ")
            .append(member.agentId())
            .append(" (")
            .append(member.agentType())
            .append(")");
      }

      sb.append("\n\nTask list (").append(taskListState.tasks().size()).append(" tasks):");
      for (var task : taskListState.tasks()) {
        sb.append("\n- [").append(task.status()).append("] ").append(task.description());
        if (task.claimedBy() != null) {
          sb.append(" (claimed by ").append(task.claimedBy()).append(")");
        }
        sb.append(" [ID: ").append(task.taskId()).append("]");
      }

      return sb.toString();
    } catch (Exception e) {
      log.error("Failed to get team status", e);
      return "Error getting team status: " + e.getMessage();
    }
  }

  @FunctionTool(description = "Send a message to a team member by their agent ID.")
  public String sendMessage(String recipientAgentId, String message) {
    try {
      componentClient
          .forEventSourcedEntity(recipientAgentId)
          .method(MessageInboxEntity::send)
          .invoke(new MessageInboxEntity.SendRequest(leadAgentId, message));

      return "Message sent to " + recipientAgentId;
    } catch (Exception e) {
      log.error("Failed to send message to {}", recipientAgentId, e);
      return "Error sending message: " + e.getMessage();
    }
  }

  @FunctionTool(description = "Stop a team member by their agent ID.")
  public String stopTeamMember(String agentId) {
    try {
      componentClient.forWorkflow(agentId).method(AutonomousAgentWorkflow::stop).invoke();

      componentClient
          .forEventSourcedEntity(teamId)
          .method(TeamEntity::removeMember)
          .invoke(agentId);

      log.info("Stopped team member {}", agentId);
      return "Team member " + agentId + " stopped.";
    } catch (Exception e) {
      log.error("Failed to stop team member {}", agentId, e);
      return "Error stopping team member: " + e.getMessage();
    }
  }

  @FunctionTool(
      description =
          "Disband the team. Stops all team members and cancels unclaimed tasks. Call this when"
              + " all work is done, before completing your own task.")
  public String disbandTeam() {
    try {
      var teamState =
          componentClient.forEventSourcedEntity(teamId).method(TeamEntity::getState).invoke();

      // Disband each member — fire-and-forget, flag checked at next iteration boundary
      for (var member : teamState.members()) {
        componentClient
            .forWorkflow(member.agentId())
            .method(AutonomousAgentWorkflow::disband)
            .invokeAsync()
            .whenComplete(
                (result, e) -> {
                  if (e != null) {
                    log.warn("Failed to disband member {}: {}", member.agentId(), e.getMessage());
                  } else {
                    log.info("Disband command accepted by member {}", member.agentId());
                  }
                });
      }

      // Cancel any available (unclaimed) tasks on the task list
      try {
        var taskListState =
            componentClient
                .forEventSourcedEntity(taskListId)
                .method(TaskListEntity::getState)
                .invoke();
        for (var task : taskListState.tasks()) {
          if (task.status() == TaskListState.TaskListItemStatus.AVAILABLE) {
            componentClient
                .forEventSourcedEntity(taskListId)
                .method(TaskListEntity::cancelTask)
                .invoke(task.taskId());
          }
        }
      } catch (Exception e) {
        log.warn("Failed to cancel unclaimed tasks", e);
      }

      // Mark the team as disbanded
      componentClient.forEventSourcedEntity(teamId).method(TeamEntity::disband).invoke();

      log.info("Team {} disbanded, {} members stopped", teamId, teamState.members().size());
      return "Team disbanded. " + teamState.members().size() + " members stopped.";
    } catch (Exception e) {
      log.error("Failed to disband team", e);
      return "Error disbanding team: " + e.getMessage();
    }
  }
}
