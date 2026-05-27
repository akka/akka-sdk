<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Autonomous Agents](../autonomous-agents.html)
- [Notifications](notifications.html)

<!-- </nav> -->

# Notifications

The runtime publishes notifications on every meaningful transition: agent lifecycle, task progress, coordination events, and derived signals like repeated failures or approaching iteration limits. Subscribe to the stream to observe agent execution from outside.

See [Agent notifications](client.html#agent-notifications) for how to subscribe.

This page is a catalog of all notification types. Two distinct families exist:

- **Autonomous agent notifications**: the <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.html">`Notification`</a> sealed interface and its records, emitted by an agent instance and observed by subscribing to its notification stream.
- **Task entity notifications**: the <a href="../_attachments/api/akka/javasdk/agent/task/TaskNotification.html">`TaskNotification`</a> sealed interface in `akka.javasdk.agent.task`, published by the task entities themselves.
Each link below points at the JavaDoc for the exact field set.

## <a href="about:blank#_autonomous_agent_notifications"></a> Autonomous agent notifications

Every autonomous agent notification implements the <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.html">`Notification`</a> sealed interface and exactly one capability-grouped marker sub-interface. Pattern-match on a marker to handle a whole family generically.

### <a href="about:blank#_lifecycle"></a> Lifecycle

<a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.LifecycleNotification.html">`Notification.LifecycleNotification`</a>: agent activation, iteration boundaries, and suspend/resume/stop transitions.

| Notification | Description |
| --- | --- |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.Activated.html">`Notification.Activated`</a> | Agent transitioned from idle to processing. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.Deactivated.html">`Notification.Deactivated`</a> | No more work, back to idle. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.IterationStarted.html">`Notification.IterationStarted`</a> | Model call beginning. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.IterationCompleted.html">`Notification.IterationCompleted`</a> | Iteration completed successfully, with token usage. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.IterationFailed.html">`Notification.IterationFailed`</a> | Iteration failed, optionally tied to a task and iteration number. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.Suspended.html">`Notification.Suspended`</a> | Agent suspended, with the source of the suspend. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.Resumed.html">`Notification.Resumed`</a> | Agent resumed, with the source of the resume. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.Stopped.html">`Notification.Stopped`</a> | Agent stopped, distinguishing operator terminate from auto-stop on queue drain. |

### <a href="about:blank#_task"></a> Task

<a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskNotification.html">`Notification.TaskNotification`</a>: assignment, start, completion, failure, cancellation, and dependency events.

| Notification | Description |
| --- | --- |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskAssigned.html">`Notification.TaskAssigned`</a> | A task was accepted or queued, fires before the agent starts working on it. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskStarted.html">`Notification.TaskStarted`</a> | Agent started working on a task. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskResultRejected.html">`Notification.TaskResultRejected`</a> | Result rejected by a [task rule](tasks.html#task-rules). |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskCompleted.html">`Notification.TaskCompleted`</a> | Task completed successfully. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskFailed.html">`Notification.TaskFailed`</a> | Task failed during execution, either by the model’s decision or because the iteration limit was reached. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskCancelled.html">`Notification.TaskCancelled`</a> | Task terminated before execution began, for example by a dependency failure or orphan cleanup. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskDependencyWait.html">`Notification.TaskDependencyWait`</a> | Task is blocked waiting for dependencies. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.DependencyResolved.html">`Notification.DependencyResolved`</a> | A specific dependency resolved, with success or failure. |

### <a href="about:blank#_handoff"></a> Handoff

<a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.HandoffNotification.html">`Notification.HandoffNotification`</a>: source-side and target-side of cross-agent handoffs.

| Notification | Description |
| --- | --- |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.HandoffStarted.html">`Notification.HandoffStarted`</a> | Source side. This agent handed off a task to a target. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.HandoffReceived.html">`Notification.HandoffReceived`</a> | Target side. This agent received a handed-off task from a source. |

### <a href="about:blank#_delegation"></a> Delegation

<a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.DelegationNotification.html">`Notification.DelegationNotification`</a>: orchestrator-side and worker-side of subtask delegation.

| Notification | Description |
| --- | --- |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.DelegationStarted.html">`Notification.DelegationStarted`</a> | Orchestrator side. A batch of subtasks was dispatched to one or more workers. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.DelegationResolved.html">`Notification.DelegationResolved`</a> | Orchestrator side. Aggregate resolution of a delegation batch. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.WorkerTaskReceived.html">`Notification.WorkerTaskReceived`</a> | Worker side. This agent accepted a delegated subtask. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.WorkerTaskCompleted.html">`Notification.WorkerTaskCompleted`</a> | Worker side. Delegated subtask finished. |

### <a href="about:blank#_team"></a> Team

<a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TeamNotification.html">`Notification.TeamNotification`</a>: team formation, member lifecycle, and disbanding.

| Notification | Description |
| --- | --- |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TeamCreated.html">`Notification.TeamCreated`</a> | Lead side. A new team was formed. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TeamMemberReady.html">`Notification.TeamMemberReady`</a> | Lead side. A member’s setup chain completed. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TeamMemberSetupFailed.html">`Notification.TeamMemberSetupFailed`</a> | Lead side. A member’s setup chain failed. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TeamMemberStopped.html">`Notification.TeamMemberStopped`</a> | Lead side. A member has stopped. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TeamDisbanded.html">`Notification.TeamDisbanded`</a> | Lead side. Team disbanded. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TeamJoined.html">`Notification.TeamJoined`</a> | Member side. This agent joined a team. |

### <a href="about:blank#_conversation"></a> Conversation

<a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.ConversationNotification.html">`Notification.ConversationNotification`</a>: conversation creation, turns, and participation lifecycle.

| Notification | Description |
| --- | --- |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.ConversationCreated.html">`Notification.ConversationCreated`</a> | Moderator side. A new conversation was created with a set of participants. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.ConversationParticipantReady.html">`Notification.ConversationParticipantReady`</a> | Moderator side. A participant’s setup completed. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.ConversationParticipantSetupFailed.html">`Notification.ConversationParticipantSetupFailed`</a> | Moderator side. A participant’s setup failed. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.ConversationEnded.html">`Notification.ConversationEnded`</a> | Moderator side. The conversation ended. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.ConversationTurnReceived.html">`Notification.ConversationTurnReceived`</a> | Moderator side. A turn was received from a participant. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.ConversationJoined.html">`Notification.ConversationJoined`</a> | Participant side. This agent joined a conversation. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.ParticipantTurnSubmitted.html">`Notification.ParticipantTurnSubmitted`</a> | Participant side. This agent submitted its turn. |

### <a href="about:blank#_messaging"></a> Messaging

<a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.MessagingNotification.html">`Notification.MessagingNotification`</a>: contact introductions and message delivery.

| Notification | Description |
| --- | --- |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.MessageReceived.html">`Notification.MessageReceived`</a> | A message was received from another agent. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.ContactAdded.html">`Notification.ContactAdded`</a> | A new contact was introduced. |

### <a href="about:blank#_struggle"></a> Struggle

<a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.StruggleNotification.html">`Notification.StruggleNotification`</a>: derived signals for repeated failures, stuck dependencies, and approaching limits. The runtime tracks counters and emits these once per detection. Notifications are deduplicated until the underlying counter resets, for example on task termination or successful recovery. Thresholds are configurable in `reference.conf` under `akka.runtime.autonomous-agent.struggle`.

| Notification | Description |
| --- | --- |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskStruggleDetected.html">`Notification.TaskStruggleDetected`</a> | Repeated iteration failures or repeated rule rejections on the same task. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskDependencyStuck.html">`Notification.TaskDependencyStuck`</a> | A task has been waiting on dependencies past the configured threshold; re-fires periodically. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.TaskApproachingMaxIterations.html">`Notification.TaskApproachingMaxIterations`</a> | A task has reached the configured fraction (default 80%) of its max iterations. |
| <a href="../_attachments/api/akka/javasdk/agent/autonomous/Notification.RepeatedIterationFailure.html">`Notification.RepeatedIterationFailure`</a> | Repeated iteration failures in flows not tied to a specific task (pre-task setup, request-based delegation). |

## <a href="about:blank#task-entity-notifications"></a> Task entity notifications

<a href="../_attachments/api/akka/javasdk/agent/task/TaskNotification.html">`akka.javasdk.agent.task.TaskNotification`</a> is published by the task entity itself on every terminal transition. This is the persistent, entity-level event used by the runtime for `TaskClient.resultAsync` and similar deliver-on-completion APIs.

Non-terminal transitions (created, assigned, started, reassigned) do not publish a notification. They are observable through event-sourced history or by reading the task snapshot.

| Notification | Description |
| --- | --- |
| <a href="../_attachments/api/akka/javasdk/agent/task/TaskNotification.Completed.html">`TaskNotification.Completed`</a> | Task completed successfully, carrying the JSON-encoded result. |
| <a href="../_attachments/api/akka/javasdk/agent/task/TaskNotification.ResultRejected.html">`TaskNotification.ResultRejected`</a> | A [task rule](tasks.html#task-rules) rejected the result. |
| <a href="../_attachments/api/akka/javasdk/agent/task/TaskNotification.Failed.html">`TaskNotification.Failed`</a> | Task failed. |
| <a href="../_attachments/api/akka/javasdk/agent/task/TaskNotification.Cancelled.html">`TaskNotification.Cancelled`</a> | Task was cancelled. |

## <a href="about:blank#_see_also"></a> See Also

- [Subscribing to the notification stream](client.html#agent-notifications)
- [Tasks](tasks.html)
- [Coordination capabilities](capabilities.html)

<!-- <footer> -->
<!-- <nav> -->
[Client API](client.html) [Testing](testing.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->