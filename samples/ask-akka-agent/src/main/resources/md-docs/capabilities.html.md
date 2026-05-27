<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Autonomous Agents](../autonomous-agents.html)
- [Coordination capabilities](capabilities.html)

<!-- </nav> -->

# Coordination capabilities

An [Autonomous Agent](../autonomous-agents.html) participates in the coordination patterns described in [Coordination patterns](coordination.html) by declaring capabilities on its `AgentDefinition`. Each capability adds tools to the agent’s tool loop. The model sees these coordination tools alongside the agent’s own domain tools and decides which to call as the work unfolds.

Each capability maps to one or more patterns:

- `Delegation` enables delegative patterns.
- `canHandoffTo` enables sequential patterns.
- `TeamLeadership` enables collaborative and emergent patterns.
- `Moderation` enables structured turn-taking conversations.

## <a href="about:blank#delegation"></a> Delegation

A coordinator declares delegation targets by adding a `Delegation` capability. The framework provides delegation tools for each combination of accepted task type and target, and the model picks one of these tools when it decides to delegate. When called, the tool creates the subtask, spawns the worker agent, assigns the task, awaits the result, and returns it to the coordinator’s tool loop.

```java
import akka.javasdk.agent.autonomous.capability.Delegation;

define()
  .capability(Delegation.to(Researcher.class, Analyst.class))
```
The coordinator pauses while workers execute, then resumes with their results. Delegated agents shut down after their task completes. The coordinator maintains full context and is responsible for synthesizing the results.

**Context flow:** Partitioned. Each worker sees only its assigned task. Workers are isolated from each other. The coordinator sees the original task and the results that come back.

**When to use:** Tasks that decompose into distinct subtasks benefiting from isolated, focused contexts. Good for parallel execution and when independent perspectives are needed.

### <a href="about:blank#_parallel_workers"></a> Parallel workers

`maxParallelWorkers` caps how many delegated tasks within a single `Delegation` capability can run at the same time. The default is configured by `akka.javasdk.agent.autonomous.delegation.max-parallel-workers` (default 3). Lower it to 1 to force sequential execution, or raise it when subtasks are genuinely independent and you want more fan-out for latency or thoroughness:

```java
define()
  .capability(Delegation.to(Researcher.class, Analyst.class).maxParallelWorkers(4));
```
To group workers so each group has its own concurrency limit, declare several `Delegation` capabilities. Workers from different capabilities run independently, each with its own `maxParallelWorkers` budget. This is useful when one set of workers, for example fast research agents, can fan out widely while another set, such as an expensive review agent, should remain serialized:

```java
define()
  .capability(Delegation.to(Researcher.class).maxParallelWorkers(5))
  .capability(Delegation.to(Reviewer.class).maxParallelWorkers(1));    // run sequentially
```
The coordinator delegates to specialist agents that each accept their own task type:

[ResearchCoordinator.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/research/application/ResearchCoordinator.java)
```java
@Component(
  id = "research-coordinator",
  description = """
  Produces comprehensive research briefs by synthesizing findings \
  from multiple specialist perspectives\
  """
)
public class ResearchCoordinator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(ResearchTasks.BRIEF).maxIterationsPerTask(5))
      .capability(Delegation.to(Researcher.class, Analyst.class).maxParallelWorkers(3));
  }
}
```
Each delegation target is a standalone agent with its own definition and accepted task types:

[Researcher.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/research/application/Researcher.java)
```java
@Component(
  id = "researcher",
  description = "Researches topics to find key facts and relevant context"
)
public class Researcher extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(ResearchTasks.FINDINGS).maxIterationsPerTask(3));
  }
}
```
[Analyst.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/research/application/Analyst.java)
```java
@Component(
  id = "analyst",
  description = "Analyses topics to identify trends and produce actionable insights"
)
public class Analyst extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(ResearchTasks.ANALYSIS).maxIterationsPerTask(3));
  }
}
```
The task types referenced above are declared with their typed result schemas. `BRIEF` is the top-level task assigned to the coordinator, `FINDINGS` and `ANALYSIS` are the subtasks the specialists accept:

[ResearchTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/research/application/ResearchTasks.java)
```java
public class ResearchTasks {

  public static final Task<ResearchBrief> BRIEF = Task
    .name("Brief")
    .description("Produce a research brief on a given topic")
    .resultConformsTo(ResearchBrief.class);

  public static final Task<ResearchFindings> FINDINGS = Task
    .name("Findings")
    .description("Research a topic and produce factual findings")
    .resultConformsTo(ResearchFindings.class)
    .rules(ResearchFindingsRule.class);

  public static final Task<AnalysisReport> ANALYSIS = Task
    .name("Analysis")
    .description("Analyse a topic and produce a trend analysis report")
    .resultConformsTo(AnalysisReport.class);
}
```

|  | The `description` in the `@Component` annotation is included in the delegation tool description so the coordinator’s model knows when to delegate to each specialist. Without a meaningful description, the model has nothing to disambiguate between specialists. |

## <a href="about:blank#handoff"></a> Handoff

An agent declares handoff targets on its `TaskAcceptance` capability. The framework provides a tool that transfers the current task to another agent. Unlike delegation, handoff transfers ownership: the current agent is done and the target agent takes over.

```java
define()
  .capability(
    TaskAcceptance.of(SupportTasks.RESOLVE)
      .canHandoffTo(BillingSpecialist.class, TechnicalSpecialist.class));
```
Handoff is peer-to-peer. The handing-off agent reassigns the task directly to the target agent and stops. The task entity updates its assignee and records the handoff context. The new agent picks up the same task with the accumulated context from the handoff.

**Context flow:** Forward. Context accumulates or transforms as it moves through the chain.

**When to use:** Routing and triage patterns where a classifier determines which specialist should handle a request. Clear stages with specialization at each stage.

The triage agent classifies requests and hands off to the appropriate specialist:

[TriageAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/support/application/TriageAgent.java)
```java
@Component(
  id = "triage-agent",
  description = """
  Classifies customer support requests and routes them to the appropriate \
  specialist via handoff\
  """
)
public class TriageAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(
        TaskAcceptance.of(SupportTasks.RESOLVE)
          .maxIterationsPerTask(3)
          .canHandoffTo(BillingSpecialist.class, TechnicalSpecialist.class)
      );
  }
}
```
Handoff targets accept the same task type. The task moves between agents:

[BillingSpecialist.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/support/application/BillingSpecialist.java)
```java
@Component(
  id = "billing-specialist",
  description = "Resolves billing disputes, payment issues, and invoice queries"
)
public class BillingSpecialist extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(SupportTasks.RESOLVE).maxIterationsPerTask(5));
  }
}
```
[TechnicalSpecialist.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/support/application/TechnicalSpecialist.java)
```java
@Component(
  id = "technical-specialist",
  description = "Diagnoses and resolves technical problems, bugs, and service outages"
)
public class TechnicalSpecialist extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(SupportTasks.RESOLVE).maxIterationsPerTask(5));
  }
}
```
All three agents share the same task type. The result schema captures the resolution category and outcome:

[SupportTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/support/application/SupportTasks.java)
```java
public class SupportTasks {

  public record SupportResolution(String category, String resolution, boolean resolved) {}

  public static final Task<SupportResolution> RESOLVE = Task
    .name("Resolve")
    .description("Resolve a customer support request")
    .resultConformsTo(SupportResolution.class);
}
```

## <a href="about:blank#moderation"></a> Moderation

A moderator agent orchestrates turn-taking conversations between participant agents. The moderator declares which agent types can participate, and the framework manages conversation setup, turn-taking, and transcript collection. Participant agents are simple: their `@Component` description identifies them to the moderator and the framework handles the conversation mechanics automatically.

```java
import akka.javasdk.agent.autonomous.capability.Moderation;

define()
  .capability(Moderation.of(
      TechnicalReviewer.class,
      StyleReviewer.class,
      ComplianceReviewer.class))
```
Configuration options:

```java
Moderation.of(Buyer.class, Seller.class)
  .maxRounds(5)                    // safety limit for directed mode (default 5)
  .maxIterationsPerTurn(10)        // max model iterations per participant turn (default 10)
  .maxConcurrentConversations(1)   // max simultaneous conversations (default 1)
```
**Context flow:** Structured turn-taking. The moderator sees the full conversation history and generates contextual prompts for each participant. Participants see the moderator’s prompt along with new entries from the conversation since their last turn, so they can respond to what others have said.

**When to use:** Multi-perspective analysis where different specialists contribute sequentially (peer review, compliance checks), or adaptive discussions where the moderator reads responses and decides the next step (negotiations, interviews).

Two conversation modes shape how the moderator controls the flow.

### <a href="about:blank#_scripted_conversations"></a> Scripted conversations

In scripted mode, the moderator’s model defines a turn sequence upfront: who speaks, in what order, and what each turn should cover. The framework then drives execution step by step. At each participant step, the moderator generates a prompt based on the conversation so far. At moderator steps, the moderator contributes its own message, for example a synthesis or summary. After all steps complete, the conversation finishes and the moderator receives the full transcript.

This mode suits structured processes with a known sequence: peer reviews where each specialist reviews in order, multi-stage assessments, or any workflow where the moderator knows the steps ahead of time but wants to generate contextual prompts as the conversation unfolds.

[ReviewModerator.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/peerreview/application/ReviewModerator.java)
```java
@Component(
  id = "review-moderator",
  description = "Coordinates peer review of documents through specialist reviewers"
)
public class ReviewModerator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(ReviewTasks.REVIEW))
      .capability(
        Moderation.of(TechnicalReviewer.class, StyleReviewer.class, ComplianceReviewer.class)
      );
  }
}
```
Participant agents need only a `@Component` description. The framework provides the conversation tools:

[TechnicalReviewer.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/peerreview/application/TechnicalReviewer.java)
```java
@Component(
  id = "technical-reviewer",
  description = "Reviews documents for technical accuracy, correctness, and completeness"
)
public class TechnicalReviewer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define();
  }
}
```

|  | The `description` in the `@Component` annotation is shown to the moderator’s model so it understands each participant’s expertise when generating prompts. Without a meaningful description, the moderator has nothing to disambiguate between participants. |
The moderator accepts a single review task that aggregates the per-reviewer findings:

[ReviewTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/peerreview/application/ReviewTasks.java)
```java
public class ReviewTasks {

  public record ReviewResult(
    String document,
    String assessment,
    List<String> reviewerFindings
  ) {}

  public static final Task<ReviewResult> REVIEW = Task.name("Review")
    .description(
      "Coordinate peer review of a document by technical, style, and compliance reviewers."
    )
    .resultConformsTo(ReviewResult.class);
}
```

### <a href="about:blank#_directed_conversations"></a> Directed conversations

In directed mode, the moderator’s model has full dynamic control over the conversation. It decides who speaks next, what direction to give them, and when to end the conversation, all based on the responses received so far. This enables adaptive flows where the conversation shape depends on its content.

`maxRounds` acts as a safety limit in directed mode. The conversation ends automatically if the round limit is reached, preventing runaway conversations.

[Facilitator.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/negotiation/application/Facilitator.java)
```java
@Component(
  id = "facilitator",
  description = """
  Facilitates negotiations by directing parties through structured rounds \
  of offers and counteroffers to reach agreement\
  """
)
public class Facilitator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(NegotiationTasks.NEGOTIATE))
      .capability(Moderation.of(Buyer.class, Seller.class).maxRounds(10));
  }
}
```
The facilitator’s task captures the negotiated outcome and the final offer:

[NegotiationTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/negotiation/application/NegotiationTasks.java)
```java
public class NegotiationTasks {

  public record NegotiationResult(String topic, String outcome, String finalOffer) {}

  public static final Task<NegotiationResult> NEGOTIATE = Task.name("Negotiate")
    .description("Facilitate a negotiation between buyer and seller.")
    .resultConformsTo(NegotiationResult.class);
}
```

### <a href="about:blank#_multiple_participants_of_the_same_type"></a> Multiple participants of the same type

The moderator’s model can use multiple instances of the same participant type with different reference names. For example, two critics with different review focuses. The reference name distinguishes them in the conversation:

```java
@Component(
  id = "critic",
  description = "Provides critical analysis and critiques from a specific perspective"
)
public class Critic extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define();
  }
}
```
The moderator declares `Critic.class` once in its `Moderation.of(…​)` capability. At runtime, the moderator’s model can create multiple participant references, for example a "technical-reviewer" and a "financial-reviewer". Both are backed by the same `Critic` agent type but operate with different context, shaped by the prompts the moderator generates for each.

## <a href="about:blank#teams"></a> Teams

Teams are the freer counterpart to moderation. A team lead forms a team with a shared task list. Members run autonomously: claiming tasks, working on them, talking to each other, and completing work independently. The lead monitors progress and disbands the team when done. Where moderation gives the moderator full control over who speaks when, a team gives members the autonomy to coordinate among themselves.

The `TeamLeadership` capability provides tools for the lead to create teams, add members, create tasks in the shared list, check team status, send messages, and disband the team. Team members get task-list and messaging tools injected automatically. Members iterate in a loop: discover tasks, claim, work, complete, check for more. They stop when the team is disbanded.

Team members can message each other directly without coordinating through the lead. Once a team is formed, every member knows about every other member, and a member can send a message to any peer to ask a question, share a result, or signal a dependency. The lead is in every member’s contact list and can be messaged like any peer, but does not automatically see messages members send to each other. The lead does observe one thing across the whole team: the shared backlog, with tasks being claimed, released, and completed.

```java
import akka.javasdk.agent.autonomous.capability.TeamLeadership;
import akka.javasdk.agent.autonomous.capability.TeamLeadership.TeamMember;

define()
  .capability(TeamLeadership.of(TeamMember.of(Developer.class).maxInstances(3)));
```
By default, the lead can only run one team at a time. Use `maxConcurrentTeams` to allow the lead to manage multiple teams simultaneously, for example, when the task requires separate teams working on independent concerns in parallel:

```java
define()
  .capability(
    TeamLeadership.of(TeamMember.of(Developer.class).maxInstances(3))
      .maxConcurrentTeams(2));
```
**Context flow:** Exchanged. Agents communicate as they work, sending messages that shape each other’s reasoning. Each agent has its own context, influenced by the messages it receives.

**When to use:** Interdependent work where agents need to see each other’s contributions, or when quality benefits from peer review. Good for collaborative problem-solving where different expertise needs to be actively integrated.

The team lead decomposes a project and waits while developers self-coordinate:

[ProjectLead.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/devteam/application/ProjectLead.java)
```java
@Component(
  id = "project-lead",
  description = "Delivers completed software projects by leading a team of developers"
)
public class ProjectLead extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .instructions(
        """
        Message team members directly when their tasks have dependencies or \
        shared interfaces that require coordination before implementation. \
        """
      )
      .capability(TaskAcceptance.of(ProjectTasks.PLAN))
      .capability(TeamLeadership.of(TeamMember.of(Developer.class).maxInstances(3)));
  }
}
```
Members claim tasks from the shared list and message peers directly when their work depends on or affects others:

[Developer.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/devteam/application/Developer.java)
```java
@Component(id = "developer", description = "Implements features with clean, tested code")
public class Developer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .instructions(
        """
        Coordinate with teammates when your work depends on or affects \
        their tasks — agree on shared contracts before implementing. \
        """
      )
      .capability(TaskAcceptance.of(DeveloperTasks.IMPLEMENT))
      .tools(new CodeTools());
  }
}
```
The lead and the members work on different task types. `PLAN` is the top-level task assigned to the lead, and `IMPLEMENT` is a `TaskTemplate` that the lead instantiates per work item for the developers to claim:

[ProjectTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/devteam/application/ProjectTasks.java)
```java
public class ProjectTasks {

  public record ProjectResult(String summary, List<String> deliverables) {}

  public static final Task<ProjectResult> PLAN = Task
    .name("Plan")
    .description("Plan project: break work into tasks, coordinate a team, and deliver results.")
    .resultConformsTo(ProjectResult.class);
}
```
[DeveloperTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/devteam/application/DeveloperTasks.java)
```java
public class DeveloperTasks {

  public static final TaskTemplate<CodeDeliverable> IMPLEMENT = TaskTemplate
    .define("Implement")
    .description("Implement a feature with clean, tested code")
    .resultConformsTo(CodeDeliverable.class)
    .instructionTemplate("Implement: {feature}. Requirements: {requirements}.");
}
```

### <a href="about:blank#_emergent_patterns"></a> Emergent patterns

The [emergent (swarm) pattern](coordination.html) has no dedicated capability. It is typically built on top of `TeamLeadership` as a **blackboard system**: direct peer messaging is replaced with a shared data space that members read from and write to. The "blackboard" can be the backlog itself, or an entity or view exposed as a tool. Members react primarily to what other members leave behind in that shared space, rather than messaging each other.

## <a href="about:blank#external-input"></a> External input

External input is not a separate capability. It is built directly on [task dependencies](tasks.html) using a task that no agent is assigned to. The framework treats that task like any other: it sits between upstream and downstream tasks, and the rest of the pipeline waits on it. An external caller (a human via an HTTP endpoint, another service via a webhook, anything outside the agent loop) completes or fails that task to release or stop the rest of the work.

**When to use:** Any decision that has to be made outside the agent loop. Human approval, manual data entry, callbacks from third-party systems, or pauses while external state catches up.

### <a href="about:blank#_human_approval_gate"></a> Human approval gate

The `publishing` sample wires this together as a three-task chain. An agent drafts a post, a human approves or rejects, an agent publishes the approved post. The human’s decision is encoded as a task with a typed result, the same as the agent-driven tasks around it.

Define the three tasks. The middle one (`APPROVAL`) carries an `ApprovalDecision` result that the human will produce:

[PublishingTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/publishing/application/PublishingTasks.java)
```java
public class PublishingTasks {

  public static final Task<DraftPost> DRAFT = Task
    .name("Draft post")
    .description("Draft a blog post on a given topic")
    .resultConformsTo(DraftPost.class);

  public static final Task<ApprovalDecision> APPROVAL = Task
    .name("Approval")
    .description("Human approval gate for publishing")
    .resultConformsTo(ApprovalDecision.class);

  public static final Task<PublishedPost> PUBLISH = Task
    .name("Publish post")
    .description("Publish an approved post")
    .resultConformsTo(PublishedPost.class);
}
```
When a request arrives, create all three tasks at once. The draft and publish tasks are assigned to autonomous agents in the same call. The approval task is created without an agent assignment and depends on the draft. The publish task depends on the approval. The dependency graph holds the pipeline together:

[PublishingEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/publishing/api/PublishingEndpoint.java)
```java
var draftTaskId = componentClient
  .forAutonomousAgent(ContentAgent.class, contentAgentId)
  .runSingleTask(
    PublishingTasks.DRAFT.instructions("Write a blog post about: " + request.topic())
  );

// 2. Create approval task (unassigned, depends on draft)
var approvalTaskId = UUID.randomUUID().toString();
componentClient
  .forTask(approvalTaskId)
  .create(
    PublishingTasks.APPROVAL.instructions(
      "Review the draft and approve or reject for publishing."
    ).dependsOn(draftTaskId)
  );

// 3. Create publish task assigned to publishing agent (depends on approval)
var publishTaskId = componentClient
  .forAutonomousAgent(PublishingAgent.class, UUID.randomUUID().toString())
  .runSingleTask(
    PublishingTasks.PUBLISH.instructions("Publish the approved post.").dependsOn(
      approvalTaskId
    )
  );
```
The content agent drafts the post and completes the draft task. The approval task is now runnable, but no agent is assigned to it, so it stays at `PENDING`. A human reviews the draft (a separate `GET /publishing/draft/{id}` endpoint reads the snapshot) and either approves or rejects through dedicated endpoints.

Approving assigns the task to the human (the assignee is just a label identifying who approved, not an agent reassignment) and completes it with an `ApprovalDecision`. The downstream publish task can now run, because its dependency completed successfully:

[PublishingEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/publishing/api/PublishingEndpoint.java)
```java
/** Human approves the draft — assigns and completes the approval task. */
@Post("/approve/{approvalTaskId}")
public String approve(String approvalTaskId, ApproveRequest request) {
  componentClient.forTask(approvalTaskId).assign(request.approvedBy());
  componentClient
    .forTask(approvalTaskId)
    .complete(
      PublishingTasks.APPROVAL,
      new ApprovalDecision(request.approvedBy(), request.comment())
    );
  return "Approved";
}
```
Rejecting assigns the task to the human and fails it. Failing a task that has dependents causes the framework to cancel the dependents (the publish task transitions to `CANCELLED`):

[PublishingEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/publishing/api/PublishingEndpoint.java)
```java
/** Human rejects the draft — assigns and fails the approval task. */
@Post("/reject/{approvalTaskId}")
public String reject(String approvalTaskId, RejectRequest request) {
  componentClient.forTask(approvalTaskId).assign(request.rejectedBy());
  componentClient.forTask(approvalTaskId).fail(request.reason());
  return "Rejected";
}
```
From the agent’s perspective there is no special waiting state to handle. The runtime simply does not start a task whose dependencies are not yet complete. The same lifecycle, snapshot, and notification machinery covers human-completed tasks as covers agent-completed ones, so an external client can poll the snapshot or react to terminal notifications regardless of who finished the task.

## <a href="about:blank#_composing_capabilities"></a> Composing capabilities

Capabilities compose freely. An agent can combine:

- **Handoff with external input.** Triage low-risk directly, hand off high-risk to a specialist that requests human approval.
- **Delegation with handoff.** Delegate to specialists for most work, hand off edge cases to a different agent type.
- **Delegation with external input.** Delegate writing and editing to specialists, request editorial approval for the final output.
- **Teams with external input.** Team members collaborate, with human approval required for final publication.
- **Moderation with delegation.** Moderate a conversation between specialists, then delegate follow-up work based on the outcome.
For example, a consulting coordinator that delegates routine research and hands off complex problems to a senior specialist:

[ConsultingCoordinator.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/consulting/application/ConsultingCoordinator.java)
```java
@Component(
  id = "consulting-coordinator",
  description = """
  Delivers actionable consulting recommendations by assessing \
  problem complexity and routing to the right expertise level\
  """
)
public class ConsultingCoordinator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .tools(new ConsultingTools())
      .capability(
        TaskAcceptance.of(ConsultingTasks.ENGAGEMENT).canHandoffTo(SeniorConsultant.class)
      )
      .capability(Delegation.to(ConsultingResearcher.class).maxParallelWorkers(2))
      .capability(Delegation.to(FactCheckAgent.class));
  }
}
```
`ENGAGEMENT` is the top-level task that the coordinator accepts and that the senior consultant takes over on handoff. `RESEARCH` is the subtask the coordinator delegates for routine investigation:

[ConsultingTasks.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/consulting/application/ConsultingTasks.java)
```java
public class ConsultingTasks {

  public record ConsultingResult(
    String assessment,
    String recommendation,
    boolean escalated
  ) {}

  public record ResearchSummary(String topic, String findings) {}

  public static final Task<ConsultingResult> ENGAGEMENT = Task
    .name("Engagement")
    .description("Consulting engagement — assess a client problem and deliver a recommendation")
    .resultConformsTo(ConsultingResult.class);

  public static final Task<ResearchSummary> RESEARCH = Task
    .name("Research")
    .description("Research a specific aspect of a client problem")
    .resultConformsTo(ResearchSummary.class);
}
```
The model decides at runtime whether to delegate a subtask (retaining ownership) or hand off the entire task (transferring ownership). Domain tools like `assessProblem` and `checkComplexity` give the model the information it needs to make this decision.

## <a href="about:blank#_see_also"></a> See Also

- [Coordination patterns](coordination.html) for the conceptual background
- [Notifications](notifications.html) for events emitted during coordination
- [Defining an autonomous agent](defining.html)

<!-- <footer> -->
<!-- <nav> -->
[Coordination patterns](coordination.html) [Client API](client.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->