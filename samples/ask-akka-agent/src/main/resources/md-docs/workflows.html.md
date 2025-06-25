<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Components](components/index.html)
- [Workflows](workflows.html)

<!-- </nav> -->

# Implementing Workflows

![Workflow](../_images/workflow.png)
Workflows implement long-running, multi-step business processes while allowing developers to focus exclusively on domain and business logic. Workflows provide durability, consistency and the ability to call other components and services. Business transactions can be modeled in one central place, and the Workflow will keep them running smoothly, or roll back if something goes wrong.

Users can see the workflow execution details in the console (both [locally](running-locally.html#_local_console) and in the [cloud](https://console.akka.io/)).

![workflow execution](_images/workflow-execution.png)


Entity and Workflow sharding [Stateful components](../reference/glossary.html#stateful_component), such as Entities and Workflows, offer strong consistency guarantees. Each stateful component can have many instances, identified by [ID](../reference/glossary.html#id). Akka distributes them across every service instance in the cluster. We guarantee that there is only one stateful component instance in the whole service cluster. If a command arrives to a service instance not hosting that stateful component instance, the command is forwarded by the Akka Runtime to the one that hosts that particular component instance. This forwarding is done transparently via [Component Client](../reference/glossary.html#component_client) logic. Because each stateful component instance lives on exactly one service instance, messages can be handled sequentially. Hence, there are no concurrency concerns, each Entity or Workflow instance handles one message at a time.

The state of the stateful component instance is kept in memory as long as it is active. This means it can serve read requests or command validation before updating without additional reads from the durable storage. There might not be room for all stateful component instances to be kept active in memory all the time and therefore least recently used instances can be passivated. When the stateful component is used again it recovers its state from durable storage and becomes an active with its system of record in memory, backed by consistent durable storage. This recovery process is also used in cases of rolling updates, rebalance, and abnormal crashes.

## <a href="about:blank#_effect_api"></a> Workflow’s Effect API

The Workflow’s Effect defines the operations that Akka should perform when an incoming command is handled by a Workflow.

A Workflow Effect can either:

- update the state of the workflow
- define the next step to be executed (transition)
- pause the workflow
- end the workflow
- fail the step or reject a command by returning an error
- reply to incoming commands
For additional details, refer to [Declarative Effects](../concepts/declarative-effects.html).

## <a href="about:blank#_modeling_state"></a> Modeling state

We want to build a simple workflow that transfers funds between two wallets. Before that, we will create a wallet subdomain with some basic functionalities that we could use later. A `WalletEntity` is implemented as an [Event Sourced Entity](event-sourced-entities.html), which is a better choice than a Key Value Entity for implementing a wallet, because a ledger of all transactions is usually required by the business.

The `Wallet` class represents domain object that holds the wallet balance. We can also withdraw or deposit funds to the wallet.

[Wallet.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow/src/main/java/com/example/wallet/domain/Wallet.java)
```java
public record Wallet(String id, int balance) {

  public Wallet withdraw(int amount) {
    return new Wallet(id, balance - amount);
  }

  public Wallet deposit(int amount) {
    return new Wallet(id, balance + amount);
  }
}
```
Domain events for creating and updating the wallet.

[WalletEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow/src/main/java/com/example/wallet/domain/WalletEvent.java)
```java
public sealed interface WalletEvent {

  @TypeName("created")
  record Created(int initialBalance) implements WalletEvent {
  }

  @TypeName("withdrawn")
  record Withdrawn(int amount) implements WalletEvent {
  }

  @TypeName("deposited")
  record Deposited(int amount) implements WalletEvent {
  }

}
```
The domain object is wrapped with a Event Sourced Entity component.

[WalletEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow/src/main/java/com/example/wallet/application/WalletEntity.java)
```java
@ComponentId("wallet")
public class WalletEntity extends EventSourcedEntity<Wallet, WalletEvent> {

  public Effect<Done> create(int initialBalance) { // (1)
    if (currentState() != null){
      return effects().error("Wallet already exists");
    } else {
      return effects().persist(new WalletEvent.Created(initialBalance))
        .thenReply(__ -> done());
    }
  }

  public Effect<Done> withdraw(int amount) { // (2)
    if (currentState() == null){
      return effects().error("Wallet does not exist");
    } else if (currentState().balance() < amount) {
      return effects().error("Insufficient balance");
    } else {
      return effects().persist(new WalletEvent.Withdrawn(amount))
          .thenReply(__ -> done());
    }
  }

  public Effect<Done> deposit(int amount) { // (3)
    if (currentState() == null){
      return effects().error("Wallet does not exist");
    } else {
      return effects().persist(new WalletEvent.Deposited(amount))
        .thenReply(__ -> done());
    }
  }

  public Effect<Integer> get() { // (4)
    if (currentState() == null){
      return effects().error("Wallet does not exist");
    } else {
      return effects().reply(currentState().balance());
    }
  }
}
```

| **1** | Create a wallet with an initial balance. |
| **2** | Withdraw funds from the wallet. |
| **3** | Deposit funds to the wallet. |
| **4** | Get current wallet balance. |
Now we can focus on the workflow implementation itself. A workflow has state, which can be updated in command handlers and step implementations. During the state modeling we might consider the information that is required for validation, running the steps, collecting data from steps or tracking the workflow progress.

[TransferState.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow/src/main/java/com/example/transfer/domain/TransferState.java)
```java
public record TransferState(Transfer transfer, TransferStatus status) {

  public record Transfer(String from, String to, int amount) { // (1)
  }

  public enum TransferStatus { // (2)
    STARTED, WITHDRAW_SUCCEED, COMPLETED
  }

  public TransferState(Transfer transfer) {
    this(transfer, STARTED);
  }

  public TransferState withStatus(TransferStatus newStatus) {
    return new TransferState(transfer, newStatus);
  }
}
```

| **1** | A `Transfer` record encapsulates data required to withdraw and deposit funds. |
| **2** | A `TransferStatus` is used to track workflow progress. |

## <a href="about:blank#_implementing_behavior"></a> Implementing behavior

Now that we have our workflow state defined, the remaining tasks can be summarized as follows:

- declare your workflow and pick a workflow id (it needs to be unique as it will be used for sharding purposes);
- implement handler(s) to interact with the workflow (e.g. to start a workflow, or provide additional data) or retrieve its current state;
- provide a workflow definition with all possible steps and transitions between them.

## <a href="about:blank#_starting_workflow"></a> Starting workflow

Let’s have a look at what our transfer workflow will look like for the first 2 points from the above list. We will now define how to launch a workflow with a `startTransfer` command handler that will return an `Effect` to start a workflow by providing a transition to the first step. Also, we will update the state with an initial value.

[TransferWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow/src/main/java/com/example/transfer/application/TransferWorkflow.java)
```java
@ComponentId("transfer") // (1)
public class TransferWorkflow extends Workflow<TransferState> { // (2)

  public record Withdraw(String from, int amount) {
  }

  public Effect<Done> startTransfer(Transfer transfer) { // (3)
    if (transfer.amount() <= 0) { // (4)
      return effects().error("transfer amount should be greater than zero");
    } else if (currentState() != null) {
      return effects().error("transfer already started");
    } else {

      TransferState initialState = new TransferState(transfer); // (5)

      Withdraw withdrawInput = new Withdraw(transfer.from(), transfer.amount());

      return effects()
        .updateState(initialState) // (6)
        .transitionTo("withdraw", withdrawInput) // (7)
        .thenReply(done()); // (8)
    }
  }
```

| **1** | Annotate such class with `@ComponentId` and pass a unique identifier for this workflow type. |
| **2** | Extend `Workflow<S>`, where `S` is the state type this workflow will store (i.e. `TransferState`). |
| **3** | Create a method to start the workflow that returns an `Effect<Done>` class. |
| **4** | The validation ensures the transfer amount is greater than zero and the workflow is not running already. Otherwise, we might corrupt the existing workflow. |
| **5** | From the incoming data we create an initial `TransferState`. |
| **6** | We instruct Akka to persist the new state. |
| **7** | With the `transitionTo` method, we inform that the name of the first step is "withdraw" and the input for this step is a `Withdraw` object. |
| **8** | The last instruction is to inform the caller that the workflow was successfully started. |

|  | The `@ComponentId` value `transfer` is common for all instances of this workflow but must be stable - cannot be changed after a production deploy - and unique across the different workflow types. |

## <a href="about:blank#_workflow_definition"></a> Workflow definition

One missing piece of our transfer workflow implementation is a workflow `definition` method, which composes all steps connected with transitions. A workflow `Step` has a unique name, an action to perform (e.g. a call to an Akka component, or a call to an external service) and a transition to select the next step (or `end` transition to finish the workflow, in case of the last step).

[TransferWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow/src/main/java/com/example/transfer/application/TransferWorkflow.java)
```java
public record Deposit(String to, int amount) {
}

final private ComponentClient componentClient;

public TransferWorkflow(ComponentClient componentClient) {
  this.componentClient = componentClient; // (2)
}

@Override
public WorkflowDef<TransferState> definition() {
  Step withdraw =
    step("withdraw") // (1)
      .call(Withdraw.class, cmd ->
        componentClient.forEventSourcedEntity(cmd.from) // (2)
          .method(WalletEntity::withdraw)
          .invoke(cmd.amount)) // (3)
      .andThen(Done.class, __ -> {
        Deposit depositInput = new Deposit(currentState().transfer().to(), currentState().transfer().amount());
        return effects()
          .updateState(currentState().withStatus(WITHDRAW_SUCCEED))
          .transitionTo("deposit", depositInput); // (4)
      });

  Step deposit =
    step("deposit") // (5)
      .call(Deposit.class, cmd ->
        componentClient.forEventSourcedEntity(cmd.to)
          .method(WalletEntity::deposit)
          .invoke(cmd.amount))
      .andThen(Done.class, __ -> {
        return effects()
          .updateState(currentState().withStatus(COMPLETED))
          .end(); // (6)
      });

  return workflow() // (7)
    .addStep(withdraw)
    .addStep(deposit);
}
```

| **1** | Each step should have a unique name. |
| **2** | Using the [ComponentClient](component-and-service-calls.html#_component_client), which is injected in the constructor. |
| **3** | We instruct Akka to run a given call to withdraw funds from a wallet. |
| **4** | After successful withdrawal we return an `Effect` that will update the workflow state and move to the next step called "deposit." An input parameter for this step is a `Deposit` record. |
| **5** | Another workflow step action to deposit funds to a given wallet. |
| **6** | This time we return an effect that will stop workflow processing, by using the special `end` method. |
| **7** | We collect all steps to form a workflow definition. |

|  | In the following example all `WalletEntity` interactions are not idempotent. It means that if the workflow step retries, it will make the deposit or withdraw again. In a real-world scenario, you should consider making all interactions idempotent with a proper deduplication mechanism. A very basic example of handling retries for workflows can be found in [this](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/wallet/domain/Wallet.java) sample. |

## <a href="about:blank#_retrieving_state"></a> Retrieving state

To have access to the current state of the workflow we can use `currentState()`. However, if this is the first command we are receiving for this workflow, the state will be `null`. We can change it by overriding the `emptyState` method. The following example shows the implementation of the read-only command handler:

[TransferWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow/src/main/java/com/example/transfer/application/TransferWorkflow.java)
```java
public ReadOnlyEffect<TransferState> getTransferState() {
  if (currentState() == null) {
    return effects().error("transfer not started");
  } else {
    return effects().reply(currentState()); // (1)
  }
}
```

| **1** | Return the current state as reply for the request. |

|  | We are returning the internal state directly back to the requester. In the endpoint, it’s usually best to convert this internal domain model into a public model so the internal representation is free to evolve without breaking clients code. |
A full transfer workflow source code sample can be downloaded as a [zip file](../java/_attachments/workflow-quickstart.zip). Follow the `README` file to run and test it.

## <a href="about:blank#_deleting_state"></a> Deleting state

If you want to delete the workflow state, you can use the `effects().delete` method. This will remove the workflow from the system.

[TransferWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow/src/main/java/com/example/transfer/application/TransferWorkflow.java)
```java
public Effect<Done> delete() {
  return effects()
    .delete() // (1)
    .thenReply(done());
}
```

| **1** | Instruction to delete the workflow. |
When you give the instruction to delete a running workflow it’s equivalent to ending and deleting a workflow. For already finished workflows, it is possible to delete them in the command handler. The actual removal of workflow state is delayed to give downstream consumers time to process all prior updates. Including the fact that the workflow has been deleted (via method annotated with `@DeleteHandler`). By default, the existence of the workflow is completely cleaned up after a week.

You can still handle read requests to the workflow until it has been completely removed, but the current state will be empty (or null). To check whether the workflow has been deleted, you can use the `isDeleted` method inherited from the `Workflow` class.

It is best to not reuse the same workflow id after deletion, but if that happens after the workflow has been completely removed it will be instantiated as a completely new workflow without any knowledge of previous state.

## <a href="about:blank#_pausing_workflow"></a> Pausing workflow

A long-running workflow can be paused while waiting for some additional information to continue processing. A special `pause` transition can be used to inform Akka that the execution of the Workflow should be postponed. By launching a Workflow command handler, the user can then resume the processing. Additionally, a Timer can be scheduled to automatically inform the Workflow that the expected time for the additional data has passed.

[TransferWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java)
```java
Step waitForAcceptation =
  step("wait-for-acceptation")
    .call(() -> {
      String transferId = currentState().transferId();
      timers().createSingleTimer(
        "acceptationTimeout-" + transferId,
        ofHours// (8),
        componentClient.forWorkflow(transferId)
          .method(TransferWorkflow::acceptationTimeout)
          .deferred()); // (1)
      return Done.done();
    })
    .andThen(Done.class, __ ->
      effects().pause()); // (2)
```

| **1** | Schedules a timer as a Workflow step action. Make sure that the timer name is unique for every Workflow instance. |
| **2** | Pauses the Workflow execution. |

|  | Remember to cancel the timer once the Workflow is resumed. Also, adjust the workflow [timeout](about:blank#_timeouts), if it has been defined, to be longer than the longest expected pause. 8 hours in this example. |
Exposing additional mutational method from the Workflow implementation should be done with special caution. Accepting a call to such method should only be possible when the Workflow is in the expected state.

[TransferWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java)
```java
public Effect<String> accept() {
  if (currentState() == null) {
    return effects().error("transfer not started");
  } else if (currentState().status() == WAITING_FOR_ACCEPTATION) { // (1)
    Transfer transfer = currentState().transfer();
    Withdraw withdrawInput = new Withdraw(currentState().withdrawId(), transfer.amount());
    return effects()
      .transitionTo("withdraw", withdrawInput)
      .thenReply("transfer accepted");
  } else { // (2)
    return effects().error("Cannot accept transfer with status: " + currentState().status());
  }
}
```

| **1** | Accepts the request only when status is `WAITING_FOR_ACCEPTATION`. |
| **2** | Otherwise, rejects the requests. |

## <a href="about:blank#_error_handling"></a> Error handling

Design for failure is one of the key attributes of all Akka components. Workflow has the richest set of configurations from all of them. It’s essential to build robust and reliable solutions.

### <a href="about:blank#_timeouts"></a> Timeouts

By default, a workflow run has no time limit. It can run forever, which in most cases is not desirable behavior. A workflow step, on the other hand, has a default timeout of 5 seconds. Both settings can be overridden at the workflow definition level or for a specific step configuration.

[TransferWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java)
```java
return workflow()
  .timeout(ofSeconds// (5)) // (1)
  .defaultStepTimeout(ofSeconds// (2)) // (2)
```

| **1** | Sets a timeout for the duration of the entire workflow. When the timeout expires, the workflow is finished and no transitions are allowed. |
| **2** | Sets a default timeout for all workflow steps. |
A default step timeout can be overridden for an individual step.

[TransferWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java)
```java
Step failoverHandler =
  step("failover-handler")
    .call(() -> {
      return "handling failure";
    })
    .andThen(String.class, __ -> effects()
      .updateState(currentState().withStatus(REQUIRES_MANUAL_INTERVENTION))
      .end())
    .timeout(ofSeconds// (1)); // (1)
```

| **1** | Overrides the step timeout for a specific step. |

### <a href="about:blank#_recover_strategy"></a> Recover strategy

It’s time to define what should happen in case of timeout or any other unhandled error.

[TransferWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java)
```java
return workflow()
  .failoverTo("failover-handler", maxRetries// (0)) // (1)
  .defaultStepRecoverStrategy(maxRetries// (1).failoverTo("failover-handler")) // (2)
  .addStep(withdraw)
  .addStep(deposit, maxRetries// (2).failoverTo("compensate-withdraw")) // (3)
```

| **1** | Set a failover transition in case of a workflow timeout. |
| **2** | Set a default failover transition for all steps with maximum number of retries. |
| **3** | Override the step recovery strategy for the `deposit` step. |

|  | In case of a workflow timeout one last failover step can be performed. Transitions from that failover step will be ignored. |

### <a href="about:blank#_compensation"></a> Compensation

The idea behind the Workflow error handling is that workflows should only fail due to unknown errors during execution. In general, you should always write your workflows so that they do not fail on any known edge cases. If you expect an error, it’s better to be explicit about it, possibly with your domain types. Based on this information and the flexible Workflow API you can define a compensation for any workflow step.

[TransferWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java)
```java
Step deposit =
  step("deposit")
    .call(Deposit.class, cmd -> {
      return componentClient.forEventSourcedEntity(currentState().transfer().to())
        .method(WalletEntity::deposit)
        .invoke(cmd);
    })
    .andThen(WalletResult.class, result -> { // (1)
      switch (result) {
        case Success __ -> {
          return effects()
            .updateState(currentState().withStatus(COMPLETED))
            .end(); // (2)
        }
        case Failure failure -> {
          return effects()
            .updateState(currentState().withStatus(DEPOSIT_FAILED))
            .transitionTo("compensate-withdraw"); // (3)
        }
      }
    });

Step compensateWithdraw =
  step("compensate-withdraw") // (4)
    .call(() -> {
      var transfer = currentState().transfer();
      String commandId = currentState().depositId();
      return componentClient.forEventSourcedEntity(transfer.from())
        .method(WalletEntity::deposit)
        .invoke(new Deposit(commandId, transfer.amount()));
    })
    .andThen(WalletResult.class, result -> {
      switch (result) {
        case Success __ -> {
          return effects()
            .updateState(currentState().withStatus(COMPENSATION_COMPLETED))
            .end(); // (5)
        }
        case Failure __ -> { // (6)
          throw new IllegalStateException("Expecting succeed operation but received: " + result);
        }
      }
    });
```

| **1** | Explicit deposit call result type `WalletResult`. |
| **2** | Finish workflow as completed, in the case of a successful deposit. |
| **3** | Launch compensation step to handle deposit failure. The `"withdraw"` step must be reversed. |
| **4** | Compensation step is like any other step, with the same set of functionalities. |
| **5** | Correct compensation can finish the workflow. |
| **6** | Any other result might be handled by a default recovery strategy. |
Compensating a workflow step(s) might involve multiple logical steps and thus is part of the overall business logic that must be defined within the workflow itself. For simplicity, in the example above, the compensation is applied only to `withdraw` step. Whereas `deposit` step itself might also require a compensation. In case of a step timeout we can’t be certain about step successful or error outcome.

A full error handling and compensation sample can be downloaded as a [zip file](../java/_attachments/workflow-quickstart.zip). Run `TransferWorkflowIntegrationTest` and examine the logs from the application.

## <a href="about:blank#_replication"></a> Multi-region replication

Stateful components like Event Sourced Entities, Key Value Entities or Workflow can be replicated to other regions. This is useful for several reasons:

- resilience to tolerate failures in one location and still be operational, even multi-cloud redundancy
- possibility to serve requests from a location near the user to provide better responsiveness
- load balancing to be able to handle high throughput
For each stateful component instance there is a primary region, which handles all write requests. Read requests can be served from any region.

Read requests are defined by declaring the command handler method with `ReadOnlyEffect` as return type. A read-only handler cannot update the state, and that is enforced at compile time.

[ShoppingCartEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-quickstart/src/main/java/shoppingcart/application/ShoppingCartEntity.java)
```java
public ReadOnlyEffect<ShoppingCart> getCart() {
  return effects().reply(currentState()); // (3)
}
```
Write requests are defined by declaring the command handler method with `Effect` as return type, instead of `ReadOnlyEffect`. Write requests are routed to the primary region and handled by the stateful component instance in that region even if the original call to the instance with the component client was made from another region.

State changes (Workflow, Key Value Entity) or events (Event Sourced Entity) persisted by the instance in the primary region are replicated to other regions and processed by corresponding instance there. This means that the state of the stateful components in all regions are updated from the primary.

The replication is asynchronous, which means that read replicas are eventually updated. Normally within a few milliseconds, but if there is for example a problem with the network between the regions it can take longer time for the read replicas to become up to date, but eventually they will.

This also means that you might not see your own writes, immediately. Consider the following:

- send a write request and that is routed to a primary in another region
- after receiving the response of the write request, you send a read request that is served by the non-primary region
- the stateful component instance in the non-primary region might not have seen the replicated changes yet, and therefore replies with "stale" information
If it’s important for some read requests to have seen latest writes you can use `Effect` for such command handler, even though it is not persisting any events. Then the request will be routed to the primary and use the latest fully consistent state.

The operational aspects are described in [Regions](../operations/regions/index.html).

<!-- <footer> -->
<!-- <nav> -->
[Views](views.html) [Timers](timed-actions.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->