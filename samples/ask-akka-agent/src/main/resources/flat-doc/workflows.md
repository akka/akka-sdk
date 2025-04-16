# Implementing Workflows

Workflows implement long-running, multi-step business processes while allowing developers to focus exclusively on domain and business logic. Workflows provide durability, consistency and the ability to call other components and services. Business transactions can be modeled in one central place, and the Workflow will keep them running smoothly, or roll back if something goes wrong.

Users can see the workflow execution details in the console (both [locally](running-locally.adoc#_local_console) and in the [cloud](https://console.akka.io)).

![workflow-execution](workflow-execution.png)

## Workflow's Effect API

The Workflow’s Effect defines the operations that Akka should perform when an incoming command is handled by a Workflow.

A Workflow Effect can either:

* update the state of the workflow
* define the next step to be executed (transition)
* pause the workflow
* end the workflow
* fail the step or reject a command by returning an error
* reply to incoming commands

For additional details, refer to [Declarative Effects](concepts:declarative-effects.adoc).

## Modeling state

We want to build a simple workflow that transfers funds between two wallets. Before that, we will create a wallet subdomain with some basic functionalities that we could use later. A `WalletEntity` is implemented as an [Event Sourced Entity](event-sourced-entities.adoc), which is a better choice than a Key Value Entity for implementing a wallet, because a ledger of all transactions is usually required by the business.

The `Wallet` class represents domain object that holds the wallet balance. We can also withdraw or deposit funds to the wallet.

**{sample-base-url}/transfer-workflow/src/main/java/com/example/wallet/domain/Wallet.java[Wallet.java]**


```

Domain events for creating and updating the wallet.

**{sample-base-url}/transfer-workflow/src/main/java/com/example/wallet/domain/WalletEvent.java[WalletEvent.java]**


```

The domain object is wrapped with a Event Sourced Entity component.

**{sample-base-url}/transfer-workflow/src/main/java/com/example/wallet/application/WalletEntity.java[WalletEntity.java]**


```
1. Create a wallet with an initial balance.
2. Withdraw funds from the wallet.
3. Deposit funds to the wallet.
4. Get current wallet balance.

Now we can focus on the workflow implementation itself. A workflow has state, which can be updated in command handlers and step implementations. During the state modeling we might consider the information that is required for validation, running the steps, collecting data from steps or tracking the workflow progress.

**{sample-base-url}/transfer-workflow/src/main/java/com/example/transfer/domain/TransferState.java[TransferState.java]**


```
1. A `Transfer` record encapsulates data required to withdraw and deposit funds.
2. A `TransferStatus` is used to track workflow progress.

## Implementing behavior

Now that we have our workflow state defined, the remaining tasks can be summarized as follows:

* declare your workflow and pick a workflow id (it needs to be unique as it will be used for sharding purposes);
* implement handler(s) to interact with the workflow (e.g. to start a workflow, or provide additional data) or retrieve its current state;
* provide a workflow definition with all possible steps and transitions between them.

## Starting workflow

Let’s have a look at what our transfer workflow will look like for the first 2 points from the above list. We will now define how to launch a workflow with a `startTransfer` command handler that will return an `Effect` to start a workflow by providing a transition to the first step. Also, we will update the state with an initial value.

**{sample-base-url}/transfer-workflow/src/main/java/com/example/transfer/application/TransferWorkflow.java[TransferWorkflow.java]**


```
1. Annotate such class with `@ComponentId` and pass a unique identifier for this workflow type.
2. Extend `Workflow<S>`, where `S` is the state type this workflow will store (i.e. `TransferState`).
3. Create a method to start the workflow that returns an `Effect<Done>` class.
4. The validation ensures the transfer amount is greater than zero and the workflow is not running already. Otherwise, we might corrupt the existing workflow.
5. From the incoming data we create an initial `TransferState`.
6. We instruct Akka to persist the new state.
7. With the `transitionTo` method, we inform that the name of the first step is "withdraw" and the input for this step is a `Withdraw` object.
8. The last instruction is to inform the caller that the workflow was successfully started.

**📌 NOTE**\
The `@ComponentId` value `transfer` is common for all instances of this workflow but must be stable - cannot be changed after a production deploy - and unique across the different workflow types.

## Workflow definition

One missing piece of our transfer workflow implementation is a workflow `definition` method, which composes all steps connected with transitions. A workflow `Step` has a unique name, an action to perform (e.g. asynchronous call to an Akka component, or asynchronous call to any external service) and a transition to select the next step (or `end` transition to finish the workflow, in case of the last step).

**{sample-base-url}/transfer-workflow/src/main/java/com/example/transfer/application/TransferWorkflow.java[TransferWorkflow.java]**


```
1. Each step should have a unique name.
2. Using the [ComponentClient](component-and-service-calls.adoc#_component_client), which is injected in the constructor.
3. We instruct Akka to run a given asynchronous call to withdraw funds from a wallet.
4. After successful withdrawal we return an `Effect` that will update the workflow state and move to the next step called "deposit." An input parameter for this step is a `Deposit` record.
5. Another workflow step action to deposit funds to a given wallet.
6. This time we return an effect that will stop workflow processing, by using special `end` method.
7. We collect all steps to form a workflow definition.

**❗ IMPORTANT**\
In the following example all `WalletEntity` interactions are not idempotent. It means that if the workflow step retries, it will make the deposit or withdraw again. In a real-world scenario, you should consider making all interactions idempotent with a proper deduplication mechanism. A very basic example of handling retries for workflows can be found in [this](https://github.com/akka/akka-sdk/blob/main/samples/transfer-workflow-compensation/src/main/java/com/example/wallet/domain/Wallet.java) sample.

## Retrieving state

To have access to the current state of the workflow we can use `currentState()`. However, if this is the first command we are receiving for this workflow, the state will be `null`. We can change it by overriding the `emptyState` method. The following example shows the implementation of the read-only command handler:

**{sample-base-url}/transfer-workflow/src/main/java/com/example/transfer/application/TransferWorkflow.java[TransferWorkflow.java]**


```
1. Return the current state as reply for the request.

**❗ IMPORTANT**\
We are returning the internal state directly back to the requester. In the endpoint, it’s usually best to convert this internal domain model into a public model so the internal representation is free to evolve without breaking clients code.

A full transfer workflow source code sample can be downloaded as a [zip file](../java/_attachments/workflow-quickstart.zip). Follow the `README` file to run and test it.

## Pausing workflow

A long-running workflow can be paused while waiting for some additional information to continue processing. A special `pause` transition can be used to inform Akka that the execution of the Workflow should be postponed. By launching a Workflow command handler, the user can then resume the processing. Additionally, a Timer can be scheduled to automatically inform the Workflow that the expected time for the additional data has passed.

**{sample-base-url}/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java[TransferWorkflow.java]**


```
1. Schedules a timer as a Workflow step action. Make sure that the timer name is unique for every Workflow instance.
2. Pauses the Workflow execution.

**📌 NOTE**\
Remember to cancel the timer once the Workflow is resumed. Also, adjust the Workflow [timeout](#timeouts) to match the timer schedule.

Exposing additional mutational method from the Workflow implementation should be done with special caution. Accepting a call to such method should only be possible when the Workflow is in the expected state.

**{sample-base-url}/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java[TransferWorkflow.java]**


```
1. Accepts the request only when status is `WAITING_FOR_ACCEPTATION`.
2. Otherwise, rejects the requests.

## Error handling

Design for failure is one of the key attributes of all Akka components. Workflow has the richest set of configurations from all of them. It’s essential to build robust and reliable solutions.

### Timeouts

By default, a workflow run has no time limit. It can run forever, which in most cases is not desirable behavior. A workflow step, on the other hand, has a default timeout of 5 seconds. Both settings can be overridden at the workflow definition level or for a specific step configuration.

**{sample-base-url}/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java[TransferWorkflow.java]**


```
1. Sets a workflow global timeout.
2. Sets a default timeout for all workflow steps.

A default step timeout can be overridden in step builder.

**{sample-base-url}/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java[TransferWorkflow.java]**


```
1. Overrides the step timeout for a specific step.

### Recover strategy

It’s time to define what should happen in case of timeout or any other unhandled error.

**{sample-base-url}/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java[TransferWorkflow.java]**


```
1. Set a failover transition in case of a workflow timeout.
2. Set a default failover transition for all steps with maximum number of retries.
3. Override the step recovery strategy for the `deposit` step.

**📌 NOTE**\
In case of a workflow timeout one last failover step can be performed. Transitions from that failover step will be ignored.

### Compensation

The idea behind the Workflow error handling is that workflows should only fail due to unknown errors during execution. In general, you should always write your workflows so that they do not fail on any known edge cases. If you expect an error, it’s better to be explicit about it, possibly with your domain types. Based on this information and the flexible Workflow API you can define a compensation for any workflow step.

**{sample-base-url}/transfer-workflow-compensation/src/main/java/com/example/transfer/application/TransferWorkflow.java[TransferWorkflow.java]**


```
1. Explicit deposit call result type `WalletResult`.
2. Finish workflow as completed, in the case of a successful deposit.
3. Launch compensation step to handle deposit failure. The `"withdraw"` step must be reversed.
4. Compensation step is like any other step, with the same set of functionalities.
5. Correct compensation can finish the workflow.
6. Any other result might be handled by a default recovery strategy.

Compensating a workflow step(s) might involve multiple logical steps and thus is part of the overall business logic that must be defined within the workflow itself. For simplicity, in the example above, the compensation is applied only to `withdraw` step. Whereas `deposit` step itself might also require a compensation. In case of a step timeout we can’t be certain about step successful or error outcome.

A full error handling and compensation sample can be downloaded as a [zip file](../java/_attachments/workflow-quickstart.zip). Run `TransferWorkflowIntegrationTest` and examine the logs from the application.
