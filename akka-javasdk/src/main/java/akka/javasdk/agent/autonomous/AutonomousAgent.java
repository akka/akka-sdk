/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.annotation.InternalApi;
import akka.javasdk.agent.task.TaskDef;
import akka.javasdk.agent.task.TaskTemplate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An autonomous agent that combines an LLM-based decision loop with durable execution.
 *
 * <p>Internally backed by a provided Workflow (for the execution loop) and a provided Agent (for
 * LLM decisions per iteration). The user configures the agent's strategy — instructions, tools,
 * execution limits, and coordination capabilities — and the framework handles the rest.
 *
 * <p>Subclasses implement {@link #configure()} to define the agent's strategy:
 *
 * <pre>{@code
 * public class ReportCoordinator extends AutonomousAgent {
 *
 *   @Override
 *   public Strategy configure() {
 *     return strategy()
 *         .instructions("Coordinate research and analysis...")
 *         .capability(delegation(
 *             agent(Researcher.class, "Research a topic in depth"),
 *             agent(Analyst.class, "Analyse data and produce insights")))
 *         .maxIterations(20);
 *   }
 * }
 * }</pre>
 */
public abstract class AutonomousAgent {

  /** A reference to an autonomous agent, used when declaring capabilities like delegation. */
  public record AgentRef(Class<? extends AutonomousAgent> agentClass, String description) {}

  /** Declare an agent reference for use in capability configuration. */
  public static AgentRef agent(Class<? extends AutonomousAgent> agentClass, String description) {
    return new AgentRef(agentClass, description);
  }

  /**
   * A team member declaration — an agent reference with team-specific constraints. Configure with
   * fluent methods:
   *
   * <pre>{@code
   * .capability(team(
   *     member(agent(Developer.class, "Writes code")).max(3),
   *     member(agent(Reviewer.class, "Reviews code")).max(1)))
   * }</pre>
   */
  public static final class TeamMember {
    private final AgentRef agentRef;
    private int maxMembers = 0;

    TeamMember(AgentRef agentRef) {
      this.agentRef = agentRef;
    }

    /** Maximum number of instances of this agent type. */
    public TeamMember max(int maxMembers) {
      this.maxMembers = maxMembers;
      return this;
    }

    public AgentRef agentRef() {
      return agentRef;
    }

    public int maxMembers() {
      return maxMembers;
    }
  }

  /** Declare a team member for use with {@link #team(TeamMember...)}. */
  public static TeamMember member(AgentRef agentRef) {
    return new TeamMember(agentRef);
  }

  // --- Shared helpers ---

  private static final Logger log = LoggerFactory.getLogger(AutonomousAgent.class);

  /** Read accepted task metadata from an agent reference by instantiating and configuring it. */
  private static List<DelegationCapability.AcceptedTaskInfo> readAcceptedTasks(AgentRef agentRef) {
    try {
      var agent = agentRef.agentClass().getDeclaredConstructor().newInstance();
      var strategy = agent.configure().toView();
      return strategy.acceptedTasks().stream()
          .map(
              taskDef -> {
                var template =
                    (taskDef instanceof TaskTemplate<?> t) ? t.instructionTemplate() : null;
                return new DelegationCapability.AcceptedTaskInfo(
                    taskDef.description(), taskDef.resultType().getName(), template);
              })
          .toList();
    } catch (Exception e) {
      log.warn(
          "Could not read accepted tasks from {}: {}",
          agentRef.agentClass().getSimpleName(),
          e.getMessage());
      return List.of();
    }
  }

  // --- Capability builders ---

  /** Interface for capability builders — each coordination pattern implements this. */
  public interface CapabilityBuilder {
    List<Capability> build();
  }

  /** Builder for delegation capabilities. */
  public static final class DelegationBuilder implements CapabilityBuilder {
    private final List<AgentRef> agents;

    DelegationBuilder(AgentRef... agents) {
      this.agents = List.of(agents);
    }

    @Override
    public List<Capability> build() {
      return agents.stream()
          .map(
              a -> {
                var acceptedTasks = readAcceptedTasks(a);
                return (Capability)
                    new DelegationCapability(
                        a.agentClass().getName(),
                        a.agentClass().getSimpleName().toLowerCase(),
                        a.description(),
                        acceptedTasks);
              })
          .toList();
    }
  }

  /** Builder for handoff capabilities. */
  public static final class HandoffBuilder implements CapabilityBuilder {
    private final List<AgentRef> agents;

    HandoffBuilder(AgentRef... agents) {
      this.agents = List.of(agents);
    }

    @Override
    public List<Capability> build() {
      return agents.stream()
          .map(
              a ->
                  (Capability)
                      new HandoffCapability(
                          a.agentClass().getName(),
                          a.agentClass().getSimpleName().toLowerCase(),
                          a.description()))
          .toList();
    }
  }

  /** Builder for team capabilities. */
  public static final class TeamBuilder implements CapabilityBuilder {
    private final List<TeamMember> members;

    TeamBuilder(TeamMember... members) {
      this.members = List.of(members);
    }

    @Override
    public List<Capability> build() {
      return members.stream()
          .map(
              m -> {
                var a = m.agentRef();
                var acceptedTasks = readAcceptedTasks(a);
                return (Capability)
                    new TeamCapability(
                        a.agentClass().getName(),
                        a.agentClass().getSimpleName().toLowerCase(),
                        a.description(),
                        m.maxMembers(),
                        acceptedTasks);
              })
          .toList();
    }
  }

  /** Create a delegation capability — the agent can delegate subtasks to these agents. */
  public static DelegationBuilder delegation(AgentRef... agents) {
    return new DelegationBuilder(agents);
  }

  /** Create a handoff capability — the agent can hand off its current task to these agents. */
  public static HandoffBuilder handoff(AgentRef... agents) {
    return new HandoffBuilder(agents);
  }

  /** Create a team capability — the agent can form a team with these agent types. */
  public static TeamBuilder team(TeamMember... members) {
    return new TeamBuilder(members);
  }

  /** Builder for external input capabilities. */
  public static final class ExternalInputBuilder implements CapabilityBuilder {
    ExternalInputBuilder() {}

    @Override
    public List<Capability> build() {
      return List.of(new ExternalInputCapability());
    }
  }

  /**
   * Create an external input capability — the agent can pause and request input from external
   * parties (human approval, another agent, or any external system).
   */
  public static ExternalInputBuilder externalInput() {
    return new ExternalInputBuilder();
  }

  /** Entry point for building a strategy. */
  public final Strategy strategy() {
    return new Strategy();
  }

  /**
   * Subclasses implement this to define the agent's strategy — what instructions it follows, what
   * tools it has, and its execution constraints.
   */
  public abstract Strategy configure();

  /**
   * The strategy defines how an autonomous agent operates: its LLM configuration, tools, execution
   * constraints, and capabilities (delegation, handoff, collaboration, etc.).
   *
   * <p>Configure with fluent methods — no terminal {@code build()} needed:
   *
   * <pre>{@code
   * return strategy()
   *     .instructions("Coordinate research and analysis...")
   *     .capability(delegation(
   *         agent(Researcher.class, "Research a topic in depth"),
   *         agent(Analyst.class, "Analyse data and produce insights")))
   *     .capability(handoff(
   *         agent(BillingSpecialist.class, "Handles billing queries")))
   *     .maxIterations(20);
   * }</pre>
   */
  public static final class Strategy {
    private final List<TaskDef<?>> acceptedTasks = new ArrayList<>();
    private String instructions = "";
    private final List<String> toolClassNames = new ArrayList<>();
    private int maxIterations = 20;
    private final List<Capability> capabilities = new ArrayList<>();

    Strategy() {}

    /** Declare which task definitions this agent accepts. */
    public Strategy accepts(TaskDef<?>... tasks) {
      this.acceptedTasks.addAll(List.of(tasks));
      return this;
    }

    /** System message defining the agent's role and instructions. */
    public Strategy instructions(String instructions) {
      this.instructions = instructions;
      return this;
    }

    /**
     * Domain tool classes available to the agent. Classes may have a no-arg constructor, or a
     * constructor taking {@code ComponentClient} for tools that need to interact with entities.
     */
    public Strategy tools(Class<?>... toolClasses) {
      for (Class<?> clz : toolClasses) {
        this.toolClassNames.add(clz.getName());
      }
      return this;
    }

    /** Maximum iterations before the agent fails. Default 20. */
    public Strategy maxIterations(int max) {
      this.maxIterations = max;
      return this;
    }

    /** Add a coordination capability to this agent's strategy. */
    public Strategy capability(CapabilityBuilder builder) {
      this.capabilities.addAll(builder.build());
      return this;
    }

    /**
     * INTERNAL API — configure shared task list access for a pre-existing task list.
     *
     * @hidden
     */
    @InternalApi
    public Strategy taskList(String taskListId) {
      this.capabilities.add(new TaskListCapability(taskListId, null));
      return this;
    }

    /**
     * INTERNAL API — read-only view of this strategy for framework use.
     *
     * @hidden
     */
    @InternalApi
    public StrategyView toView() {
      return new StrategyView(
          List.copyOf(acceptedTasks),
          instructions,
          List.copyOf(toolClassNames),
          maxIterations,
          List.copyOf(capabilities));
    }
  }

  /**
   * INTERNAL API — read-only view of a strategy, used by the framework to read configuration.
   *
   * @hidden
   */
  @InternalApi
  public record StrategyView(
      List<TaskDef<?>> acceptedTasks,
      String instructions,
      List<String> toolClassNames,
      int maxIterations,
      List<Capability> capabilities) {}
}
