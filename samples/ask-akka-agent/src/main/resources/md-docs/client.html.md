<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Autonomous Agents](../autonomous-agents.html)
- [Client API](client.html)

<!-- </nav> -->

# Client API

Autonomous agents and tasks are managed through the `ComponentClient`. This page covers how to start agents, assign tasks, query results, and observe execution from the outside.

## <a href="about:blank#_running_a_single_task"></a> Running a single task

The simplest pattern: create a task, start an agent, and automatically stop the agent when done. `runSingleTask` handles all of this in one call.

[QuestionEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/helloworld/api/QuestionEndpoint.java)
```java
var taskId = componentClient
  .forAutonomousAgent(QuestionAnswerer.class, agentInstanceId)
  .runSingleTask(QuestionTasks.ANSWER.instructions(request.question()));
```
This returns the task id for later status checks. Each call spins up an independent agent instance.

`runSingleTask` only constrains how the work starts. It provides exactly one task to the agent. Once the agent is running, its capabilities can still create more tasks during the loop. A coordinator with a `Delegation` capability creates subtasks for its workers, and a team lead with `TeamLeadership` creates tasks in the team’s shared list. The agent stops automatically once its task queue is fully drained, including the original task and anything spawned along the way.

## <a href="about:blank#_managing_tasks_and_agents_separately"></a> Managing tasks and agents separately

For more control over multiple tasks, pipelines, or long-lived agents, create tasks and assign them separately.

**Create tasks:**

```java
var taskId = UUID.randomUUID().toString();
componentClient
  .forTask(taskId)
  .create(PipelineTasks.COLLECT.instructions("Collect data on: " + topic));
```
**Assign tasks to an agent:**

[PipelineEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/autonomous-agent-playground/src/main/java/demo/pipeline/api/PipelineEndpoint.java)
```java
componentClient
  .forAutonomousAgent(ReportAgent.class, agentInstanceId)
  .assignTasks(collectTaskId, analyzeTaskId, reportTaskId);
```
Tasks are queued if the agent is busy. A single agent instance processes one task at a time. Only one task is `IN_PROGRESS`, and the rest stay `ASSIGNED` until they reach the head of the queue and their dependencies are met. Parallelism comes from fanning out to other agent instances, not from a single agent running multiple tasks concurrently.

The `maxParallelWorkers` option on a `Delegation` capability (see [Delegation](capabilities.html#delegation)) caps how many worker instances a coordinator can run in parallel. It does not change the single-task-at-a-time behavior of any one instance.

If the agent uses [dynamic configuration](defining.html#dynamic-configuration), call `setup(…​)` on the same `forAutonomousAgent(…​)` chain before `runSingleTask` or `assignTasks` so the runtime applies the per-instance instructions and capabilities before iteration begins.

**Terminate an agent:**

```java
componentClient
  .forAutonomousAgent(ReportAgent.class, agentInstanceId)
  .terminate();
```

|  | When you manage tasks and agents separately, you are responsible for stopping the agent. Autonomous agents do not auto-passivate when their queue drains. An agent that is not stopped holds its in-memory state until you call `terminate()`, even though it is idle and making no model calls. The `runSingleTask` shortcut handles this automatically. The explicit `assignTasks` flow does not. Suspend is another way to release the actor: a paused agent passivates after a short delay, and resume rehydrates it. |

## <a href="about:blank#_task_lifecycle_operations"></a> Task lifecycle operations

Beyond `create` and `get`, the `forTask(…​)` client exposes the operations that drive a task through its lifecycle: `assign`, `complete`, and `fail`. The runtime calls these on the agent’s behalf as it picks up, completes, or fails tasks during iteration. Application code calls the same operations when an external actor (typically a human) drives a task that no agent is assigned to. See [External input](capabilities.html#external-input) for the human-approval pattern that uses them end to end.

**Assign a task to an owner:**

```java
componentClient.forTask(taskId).assign("alice@example.com");
```
A task must be assigned before it can be completed or failed. The assignee is a free-form identifier (a user id, team name, or process identifier) that records who took ownership.

**Complete a task with a typed result:**

```java
componentClient
  .forTask(taskId)
  .complete(PublishingTasks.APPROVAL, new ApprovalDecision("alice", "Looks good"));
```
The result is validated against the task definition’s result type. The task transitions to `COMPLETED` and any dependents become eligible to start.

**Fail a task with a reason:**

```java
componentClient.forTask(taskId).fail("Approval rejected: tone is too informal");
```
The task transitions to `FAILED`. Any dependents are cancelled automatically.

## <a href="about:blank#_suspend_and_resume"></a> Suspend and resume

A running agent can be suspended and later resumed. While suspended, the agent stops iterating: it will not start new model calls or process queued tasks. Tasks already assigned remain in the queue and are processed when the agent resumes.

```java
// Suspend the agent
componentClient
  .forAutonomousAgent(ReportAgent.class, agentInstanceId)
  .suspend();

// Resume the agent
componentClient
  .forAutonomousAgent(ReportAgent.class, agentInstanceId)
  .resume();
```

## <a href="about:blank#_agent_state"></a> Agent state

Query the current state of an agent instance with `getState()`. The returned `AgentState` provides a snapshot of the agent’s execution status.

```java
var state = componentClient
  .forAutonomousAgent(ReportAgent.class, agentInstanceId)
  .getState();

state.phase();           // execution phase, e.g. "model", "tools", "stopped"
state.suspended();       // whether the agent is suspended
state.instructions();    // the agent's current instructions
state.totalTokenUsage(); // cumulative token usage (inputTokens, outputTokens)
state.currentTask();     // Optional<TaskKey> with the task currently being worked on
state.pendingTaskIds();  // List<String> with ids of tasks queued but not yet started
```
The `currentTask()` returns an `Optional<TaskKey>` containing the `id` and `name` of the task the agent is actively processing. When the agent is idle or between tasks, it is empty.

## <a href="about:blank#_querying_task_results"></a> Querying task results

Task results are typed based on the task definition’s `resultConformsTo` type. Two forms are available: `get(…​)` returns a snapshot of the current state, while `result(…​)` blocks until the task reaches a terminal state and returns the typed result directly.

**Read a snapshot:**

```java
var snapshot = componentClient.forTask(taskId).get(ResearchTasks.BRIEF);
if (snapshot.status() == TaskStatus.COMPLETED) {
  ResearchBrief brief = snapshot.result().orElseThrow();
  var title = brief.title();
  var findings = brief.keyFindings();
}
```
**Wait for the terminal result:**

```java
ResearchBrief brief = componentClient.forTask(taskId).result(ResearchTasks.BRIEF);
```
`result(…​)` blocks the calling thread until the task is `COMPLETED`, `FAILED`, or `CANCELLED`. It returns the typed result on success, or throws `TaskException.Failed` / `TaskException.Cancelled` on failure or cancellation. Use it when the caller wants to wait for the outcome rather than poll. In non-blocking code paths, prefer the async variant covered in [Asynchronous execution](about:blank#asynchronous-execution).

## <a href="about:blank#agent-notifications"></a> Agent notifications

Subscribe to notifications for an agent instance to observe its execution progress in real time. Notifications are published by the runtime, not by user code, as the agent moves through its execution loop and participates in coordination patterns.

```java
componentClient
  .forAutonomousAgent(QuestionAnswerer.class, agentInstanceId)
  .notificationStream()
  .runForeach(System.out::println, materializer);
```
Subscribe before triggering the agent to avoid missing early events. The stream stays open as long as the agent instance exists. Use it for dashboards, logging, cost tracking, or coordinating external processes with agent progress.

Every notification implements the `Notification` sealed interface and exactly one capability-grouped marker sub-interface. Pattern-match on a marker to handle a whole family generically:

```java
agentClient.notificationStream().runForeach(n -> {
  switch (n) {
    case Notification.LifecycleNotification lifecycle -> renderLifecycle(lifecycle);
    case Notification.TaskNotification task -> renderTask(task);
    case Notification.TeamNotification team -> renderTeam(team);
    default -> { /* ignore */ }
  }
}, materializer);
```
For the full set of notification types and their fields, see [Notifications](notifications.html).

|  | The notification stream is a live stream that emits events only after the client creates the stream. It does not replay historical notifications. While the stream is running, it delivers events in order without loss. If the stream detects missing events it fails, allowing clients to reconnect and recover. |

|  | Notifications should not be used to drive business logic. Akka does not guarantee delivery of every notification: events may be lost due to network issues, client disconnections, or other transient failures. If your application requires reliable state, query the task snapshot or agent state through the `ComponentClient`. Use notifications for observability, dashboards, and progress UIs, not as the source of truth. |

## <a href="about:blank#asynchronous-execution"></a> Asynchronous execution

The `ComponentClient` calls in this page use the synchronous form, which is the right default for most code. Each call has an `*Async` variant that returns a `CompletionStage<T>` instead. Use the async form when you want to start work and continue without blocking, fan out several calls in parallel, or compose with other asynchronous code:

```java
var stateF = componentClient
  .forAutonomousAgent(ReportAgent.class, agentInstanceId)
  .getStateAsync();

stateF.thenAccept(state -> log.info("Phase: {}", state.phase()));
```
The same shape applies to `suspendAsync()`, `resumeAsync()`, `terminateAsync()`, `assignTasksAsync(…​)`, `runSingleTaskAsync(…​)`, and the `forTask` operations. Each returns a `CompletionStage` that completes once the runtime acknowledges the operation. Mixing styles is fine: use synchronous calls where they read more clearly, async ones where the call needs to compose with other futures.

## <a href="about:blank#_see_also"></a> See Also

- [Notifications](notifications.html) for the full notification reference
- [Tasks](tasks.html)
- [Testing](testing.html)

<!-- <footer> -->
<!-- <nav> -->
[Coordination capabilities](capabilities.html) [Notifications](notifications.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->