/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.impl.JsonSchema;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provided Agent component that executes one iteration of an autonomous agent's strategy.
 *
 * <p>Called by the AutonomousAgentWorkflow each iteration. Receives the strategy configuration —
 * instructions, tool class names, task context, and delegation configs — in the request.
 * Instantiates tool objects from class names and builds the agent effect dynamically.
 *
 * <p>All coordination (delegation, etc.) is handled within the agent's internal tool call loop.
 * Delegations run to completion synchronously within the tool call.
 */
@Component(
    id = "akka-strategy-executor",
    name = "StrategyExecutor",
    description = "Executes one iteration of an autonomous agent's strategy")
public final class StrategyExecutor extends Agent {

  private static final Logger log = LoggerFactory.getLogger(StrategyExecutor.class);

  private final ComponentClient componentClient;

  public StrategyExecutor(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  /** Everything the executor needs to run one iteration. */
  public record ExecuteRequest(
      String taskId,
      String taskDescription,
      int iteration,
      int maxIterations,
      String instructions,
      List<String> toolClassNames,
      String workflowId,
      List<Capability> capabilities,
      String resultTypeName,
      List<String> handoffContext,
      String lastDecisionResponse) {}

  /** Execute one iteration: instantiate tools, build effect from strategy config. */
  public Effect<String> execute(ExecuteRequest request) {
    var isTeamMember = request.taskId() == null;

    var userMessage = new StringBuilder();

    if (isTeamMember) {
      userMessage
          .append("Iteration: ")
          .append(request.iteration())
          .append(" of ")
          .append(request.maxIterations())
          .append(
              "\n\nYou are a team member. Work from the shared task list."
                  + " Keep working until you are told the team is disbanded.")
          .append(
              "\n\nCRITICAL — ITERATION PROTOCOL:"
                  + " Each iteration is one turn. Between iterations, your teammates get"
                  + " time to read and respond to your messages."
                  + " Do ONE focused action per iteration, then STOP."
                  + " For example: claim a task, OR send one message, OR complete a task."
                  + " Do NOT send multiple messages in the same iteration —"
                  + " your teammates cannot see them until the next iteration anyway."
                  + " Do NOT repeat actions you already performed in a previous iteration.");

      // Budget-aware wrap-up nudge
      int remaining = request.maxIterations() - request.iteration();
      if (remaining <= 2) {
        userMessage.append(
            "\n\nURGENT: You have "
                + remaining
                + " iteration(s) left."
                + " You MUST complete your claimed task NOW with your best result."
                + " Call completeClaimedTask immediately.");
      } else if (remaining <= request.maxIterations() / 3) {
        userMessage.append(
            "\n\nYou are running low on iterations. Start wrapping up —"
                + " finish your current work and complete your claimed task soon.");
      }
    } else {
      userMessage
          .append("Task: ")
          .append(request.taskDescription())
          .append("\nTask ID: ")
          .append(request.taskId())
          .append("\nIteration: ")
          .append(request.iteration())
          .append(" of ")
          .append(request.maxIterations())
          .append("\n\nAnalyse the task and take action. Use the available tools.")
          .append(" When done, call complete_task with the result.")
          .append(" If you cannot complete the task, call fail_task with a reason.");
    }

    // Add result type schema information if available (not for team members)
    var resultTypeName = request.resultTypeName();
    if (!isTeamMember
        && resultTypeName != null
        && !resultTypeName.isEmpty()
        && !resultTypeName.equals(String.class.getName())) {
      try {
        var resultClass = Class.forName(resultTypeName);
        var schema = JsonSchema.jsonSchemaStringFor(resultClass);
        userMessage
            .append(
                "\n\nThe result for complete_task must be a JSON object conforming to this"
                    + " schema:\n")
            .append(schema);
      } catch (Exception e) {
        log.warn("Could not generate schema for result type: {}", resultTypeName, e);
      }
    }

    // Include handoff context from previous agents (not for team members)
    var handoffContext = request.handoffContext();
    if (!isTeamMember && handoffContext != null && !handoffContext.isEmpty()) {
      userMessage.append("\n\nContext from previous agents:");
      for (int i = 0; i < handoffContext.size(); i++) {
        userMessage.append("\n").append(i + 1).append(". ").append(handoffContext.get(i));
      }
    }

    // Include decision response if resuming after a decision point (not for team members)
    var lastDecisionResponse = request.lastDecisionResponse();
    if (!isTeamMember && lastDecisionResponse != null && !lastDecisionResponse.isEmpty()) {
      userMessage.append("\n\nDecision response received: ").append(lastDecisionResponse);
      userMessage.append("\nPlease continue with the task using this input.");
    }

    // On subsequent iterations, nudge the agent to resolve (not for team members — they iterate)
    if (!isTeamMember && request.iteration() > 1) {
      userMessage.append(
          "\n\nThis is a follow-up iteration. If you have all the information needed,"
              + " complete the task now. If you are truly blocked, fail the task with a"
              + " clear reason rather than continuing indefinitely.");
    }

    // Instantiate domain tools from class names
    var allTools = new ArrayList<Object>();
    for (String className : request.toolClassNames()) {
      try {
        var clz = Class.forName(className);
        try {
          // Try ComponentClient constructor first — allows tools to interact with entities
          var ctor = clz.getDeclaredConstructor(ComponentClient.class);
          allTools.add(ctor.newInstance(componentClient));
        } catch (NoSuchMethodException e) {
          // Fall back to no-arg constructor
          allTools.add(clz.getDeclaredConstructor().newInstance());
        }
      } catch (Exception e) {
        log.error("Failed to instantiate tool class: {}", className, e);
        throw new RuntimeException("Failed to instantiate tool class: " + className, e);
      }
    }

    // Add built-in tools (complete/fail task) — not for team members, they use task list tools
    if (!isTeamMember) {
      allTools.add(new BuiltInTools(componentClient, request.taskId(), request.resultTypeName()));
    }

    // Add decision tools only if the agent has the external input capability
    var hasDecisionCapability =
        request.capabilities().stream().anyMatch(c -> c instanceof ExternalInputCapability);

    if (hasDecisionCapability) {
      allTools.add(new DecisionTools(componentClient, request.taskId()));
      userMessage.append(
          "\n\nYou can request external decisions (approval, confirmation, clarification)"
              + " using the requestDecision tool. The task will pause until input is provided.");
    }

    // Add tools for each capability
    var delegationConfigs =
        request.capabilities().stream()
            .filter(c -> c instanceof DelegationCapability)
            .map(c -> (DelegationCapability) c)
            .toList();

    if (!delegationConfigs.isEmpty()) {
      allTools.add(new DelegationTools(componentClient, delegationConfigs));

      userMessage.append(
          "\n\nYou can delegate subtasks to specialist agents using the delegate tool.");
      userMessage.append(" Available agents:");
      for (var config : delegationConfigs) {
        userMessage
            .append("\n- ")
            .append(config.agentComponentId())
            .append(": ")
            .append(config.description());
      }
    }

    // Add tools for handoff capabilities
    var handoffConfigs =
        request.capabilities().stream()
            .filter(c -> c instanceof HandoffCapability)
            .map(c -> (HandoffCapability) c)
            .toList();

    if (!handoffConfigs.isEmpty()) {
      allTools.add(new HandoffTools(componentClient, handoffConfigs, request.taskId()));

      userMessage.append(
          "\n\nYou can hand off this task to a specialist agent using the handoffTask tool.");
      userMessage.append(
          " When you hand off, provide context about what you've learned and what the next");
      userMessage.append(" agent should focus on. You will stop working on the task.");
      userMessage.append(" Available agents for handoff:");
      for (var config : handoffConfigs) {
        userMessage
            .append("\n- ")
            .append(config.agentComponentId())
            .append(": ")
            .append(config.description());
      }
    }

    // Add tools for team capabilities (team lead)
    var teamConfigs =
        request.capabilities().stream()
            .filter(c -> c instanceof TeamCapability)
            .map(c -> (TeamCapability) c)
            .toList();

    if (!teamConfigs.isEmpty()) {
      allTools.add(new TeamTools(componentClient, teamConfigs, request.workflowId()));

      userMessage.append(
          "\n\nYou can form and manage a team. Use createTeam to set up a team and task list,");
      userMessage.append(" addTeamMember to add members, addTask to add tasks to the shared list,");
      userMessage.append(" getTeamStatus to monitor progress, and disbandTeam when all work is");
      userMessage.append(" done (disband before completing your own task). Available agent types:");
      for (var config : teamConfigs) {
        userMessage
            .append("\n- ")
            .append(config.agentComponentId())
            .append(": ")
            .append(config.description());
        if (config.maxMembers() > 0) {
          userMessage.append(" (max ").append(config.maxMembers()).append(")");
        }
      }

      // Framework-level iteration guidance for team leads.
      // The LLM tool loop runs within a single iteration — team members don't get
      // time to work until this iteration ends and the next one begins.
      userMessage.append(
          "\n\nCRITICAL — TEAM ITERATION PROTOCOL:"
              + " Each iteration is one turn. Between iterations, your team members get"
              + " time to work. To YIELD means: just respond with a brief text status"
              + " message — do NOT call complete_task, fail_task, or any other tool."
              + " You will get another iteration automatically.");
      if (request.iteration() == 1) {
        userMessage.append(
            " This is iteration 1: create your team, add members, and add tasks."
                + " Then YIELD — do NOT call getTeamStatus, disbandTeam, or complete_task."
                + " Your team members have not had any time to work.");
      } else {
        userMessage.append(
            " This is iteration "
                + request.iteration()
                + ": check getTeamStatus."
                + " If tasks are still in progress, YIELD — do not take further action."
                + " Only disband and complete_task when ALL tasks show as completed.");
      }
    }

    // Add tools for task list capabilities (team member or standalone)
    var taskListConfigs =
        request.capabilities().stream()
            .filter(c -> c instanceof TaskListCapability)
            .map(c -> (TaskListCapability) c)
            .toList();

    if (!taskListConfigs.isEmpty()) {
      var taskListConfig = taskListConfigs.getFirst();
      allTools.add(
          new TaskListTools(componentClient, taskListConfig.taskListId(), request.workflowId()));

      userMessage.append(
          "\n\nYou have access to a shared task list. Use listAvailableTasks to see work,");
      userMessage.append(" claimTask to take ownership, completeClaimedTask when done, and");
      userMessage.append(" getTaskListStatus for an overview.");
    }

    // Read unread messages if messaging capability present
    var messageCaps =
        request.capabilities().stream()
            .filter(c -> c instanceof MessageCapability)
            .map(c -> (MessageCapability) c)
            .toList();

    if (!messageCaps.isEmpty()) {
      // Read inbox for unread messages
      List<MessageInboxState.InboxMessage> unreadMessages = List.of();
      try {
        unreadMessages =
            componentClient
                .forEventSourcedEntity(request.workflowId())
                .method(MessageInboxEntity::getUnread)
                .invoke();

        if (!unreadMessages.isEmpty()) {
          // Mark messages as read
          componentClient
              .forEventSourcedEntity(request.workflowId())
              .method(MessageInboxEntity::markRead)
              .invoke(unreadMessages.size());
        }
      } catch (Exception e) {
        log.debug("No inbox for {} (may not exist yet)", request.workflowId());
      }

      // Add message tools for team members (team lead messaging is in TeamTools)
      if (teamConfigs.isEmpty()) {
        var msgCap = messageCaps.getFirst();
        allTools.add(new MessageTools(componentClient, msgCap.teamId(), request.workflowId()));
        userMessage.append("\n\nYou can send messages to teammates using sendMessage.");
      }

      // Include unread messages in user message
      if (!unreadMessages.isEmpty()) {
        userMessage.append("\n\nMessages from teammates:");
        for (var msg : unreadMessages) {
          userMessage.append("\n- From ").append(msg.from()).append(": ").append(msg.content());
        }
      }

      // Show current team members
      try {
        var msgCap = messageCaps.getFirst();
        var teamState =
            componentClient
                .forEventSourcedEntity(msgCap.teamId())
                .method(TeamEntity::getState)
                .invoke();
        if (!teamState.members().isEmpty()) {
          userMessage.append("\n\nCurrent team members:");
          for (var member : teamState.members()) {
            userMessage
                .append("\n- ")
                .append(member.agentId())
                .append(" (")
                .append(member.agentType())
                .append(")");
          }
        }
      } catch (Exception e) {
        log.debug("Could not read team state for messaging context");
      }
    }

    var builder = effects();

    if (request.instructions() != null && !request.instructions().isEmpty()) {
      builder = builder.systemMessage(request.instructions());
    }

    return builder.tools(allTools).userMessage(userMessage.toString()).thenReply();
  }

  /** Built-in tools for task completion — always available. */
  public static class BuiltInTools {

    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private final ComponentClient componentClient;
    private final String taskId;
    private final String resultTypeName;

    public BuiltInTools(ComponentClient componentClient, String taskId, String resultTypeName) {
      this.componentClient = componentClient;
      this.taskId = taskId;
      this.resultTypeName = resultTypeName;
    }

    @FunctionTool(
        description =
            "Complete the current task with a result. The result should be a JSON string if a"
                + " specific result type is required, or a plain text description otherwise.")
    public String completeTask(String result) {
      // Runtime type check: if a typed result is expected, validate before completing
      if (resultTypeName != null
          && !resultTypeName.isEmpty()
          && !resultTypeName.equals(String.class.getName())) {
        try {
          var resultClass = Class.forName(resultTypeName);
          OBJECT_MAPPER.readValue(result, resultClass);
        } catch (ClassNotFoundException e) {
          log.warn("Result type class not found: {}", resultTypeName);
          // Allow completion — we can't validate if the class isn't available
        } catch (Exception e) {
          // Deserialization failed — nudge the LLM with the expected schema
          var schema = "";
          try {
            var resultClass = Class.forName(resultTypeName);
            schema = JsonSchema.jsonSchemaStringFor(resultClass);
          } catch (Exception schemaEx) {
            // fall back to just the type name
          }
          return "ERROR: The result does not conform to the expected type '"
              + resultTypeName.substring(resultTypeName.lastIndexOf('.') + 1)
              + "'. "
              + e.getMessage()
              + (schema.isEmpty()
                  ? ""
                  : "\n\nThe result must be a JSON object conforming to this schema:\n" + schema)
              + "\n\nPlease call complete_task again with a valid JSON result.";
        }
      }

      componentClient.forEventSourcedEntity(taskId).method(TaskEntity::complete).invoke(result);
      return "Task completed successfully";
    }

    @FunctionTool(description = "Fail the current task with a reason")
    public String failTask(String reason) {
      componentClient.forEventSourcedEntity(taskId).method(TaskEntity::fail).invoke(reason);
      return "Task failed";
    }
  }
}
