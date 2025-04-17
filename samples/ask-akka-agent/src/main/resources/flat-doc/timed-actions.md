# Timers

Timers enable the scheduling of calls for future execution, making them particularly useful for verifying the completion status of processes at a later time.

Timers are stored by the [Akka Runtime](reference:glossary.adoc#runtime) and are guaranteed to run at least once.

When a timer is triggered, it initiates the scheduled call. If the call succeeds, the timer completes and is automatically removed. In case of a failure, the timer is rescheduled, continuing until the call is successful.

***Key features of timers***:

* Guaranteed to run at least once.
* Can be scheduled to run at any future time.
* Can be canceled if no longer needed.
* Automatically removed upon successful completion.
* Rescheduled after failures, with a configurable limit on retry attempts.

***Limitations of timers***:

* Maximum allowed payload size is 1024 bytes.
* Each service can have up to 50,000 active timers.
* Scheduled calls identify the component by component id and the method by its name. Changes to these may prevent the scheduled call from executing.
* Method parameter types must remain consistent after scheduling a call.

You can schedule calls to any method accessible through the `ComponentClient`, including command handlers on Event Sourced Entities, Key-Value Entities, Workflows, and Timed Actions.

To schedule a call, inject both `TimerScheduler` and `ComponentClient` into your component. These dependencies are available for injection in Service Setup, Endpoints, Consumers, Timed Actions, and Workflows. For more details, see [dependency injection](java:setup-and-dependency-injection.adoc#_dependency_injection).

## Timed Actions

Timed Actions are stateless components designed for scheduling functions to execute at future times. They serve as integration points for coordinating scheduled calls without storing state, unlike Entities and Workflows, and without direct data access like Consumers and Views. 

Within a Timed Action, you can access `ComponentClient` and compose calls to other components like Event Sourced Entities, Key-Value Entities, Workflows, and Views.

### Timed Action’s Effect API

The Timed Action’s Effect API defines actions that Akka should execute when a Timed Action method is invoked.

A Timed Action Effect can either:

* return `Done`, confirming the scheduled call completed successfully
* return an error message if the operation failed

For additional details, refer to [Declarative Effects](concepts:declarative-effects.adoc).

## Scheduling a timer

To illustrate the usage of timers, consider an Ordering Service composed of a [Key-Value Entity](key-value-entities.adoc) and a Timed Action component, where the Timed Action manages unconfirmed order cancellations.

In this scenario, users place an order that requires confirmation within a set timeframe, similar to a food ordering app where a restaurant confirms or rejects an order. If confirmation is not received within the specified period, the order is automatically canceled.

The `OrderEndpoint` acts as a controller for the Order Entity, creating a timer before passing the request. The timer is scheduled using `akka.javasdk.timer.TimerScheduler`, which you can inject into your component’s constructor.

**{sample-base-url}/reliable-timers/src/main/java/com/example/api/OrderEndpoint.java[OrderEndpoint.java]**


```
1. Declares `TimerScheduler` alongside `ComponentClient`, both provided by Akka.
2. Generates a unique identifier for the order and timer.
3. Calls the `TimerScheduler` API to register a new timer, which returns `CompletionStage<Done>`. A successful completion indicates that Akka registered the timer.
4. Uses the order id to generate a unique timer name.
5. Sets the timer delay.
6. Schedules a deferred call to the Timed Action component, covered next.
7. Composes the `timerRegistration` `CompletionStage` with a call to `OrderEntity` to place the order.

Akka registers the timer before the order is placed. This ensures that, if timer registration fails due to network issues, no untracked order remains. The inverse failure scenario — registering the timer but failing to place the order — is mitigated by handling potential failures in the `OrderEntity.cancel` method ([see further](#_cancel_order_impl)).

For reference, here is the `OrderEntity.placeOrder` method implementation.

**{sample-base-url}/reliable-timers/src/main/java/com/example/application/OrderEntity.java[OrderEntity.java]**


```
1. The `placeOrder` method initiates an order.
2. Sets the `placed` field to `true`.

## Handling the timer call

Now let’s examine the `OrderTimedAction.expireOrder` method.

**{sample-base-url}/reliable-timers/src/main/java/com/example/application/OrderTimedAction.java[OrderTimedAction.java]**


```
1. Uses the `@ComponentId` annotation to identify the component.
2. Extends the `TimedAction` class.
3. Initiates an asynchronous call to `OrderEntity` to cancel the order.
4. Determines if the call should recover or fail. If `NotFound` or `Invalid` is returned, the timer is marked obsolete and is not rescheduled. Other errors cause `expireOrder` to fail, and the timer is rescheduled.

**❗ IMPORTANT**\
Any method executed by a timer must handle errors carefully. Unhandled errors may lead to continuous re-scheduling. Ensure failures are propagated only when retrying the call is intended.

<a name="_cancel_order_impl"></a>Here is the `OrderEntity.cancel` method for reference.

**{sample-base-url}/reliable-timers/src/main/java/com/example/application/OrderEntity.java[OrderEntity.java]**


```
1. Returns `NotFound` if the order was never placed.
2. Returns `Invalid` if the order is confirmed.
3. Otherwise, clears the entity state and returns `Ok`.

Since this method is intended to be called by a timer, it must not fail. The `OrderEntity.cancel` method always returns a successful result, even when returning `NotFound` or `Invalid`, ensuring that the timer considers the call successful and does not re-schedule it. If the command handler were to throw an exception or return a `effects().error()`, the timer would interpret this as a failure and would re-schedule the call.

## Failures and retries

If a scheduled call fails, it retries with an exponential backoff, starting at 3 seconds and maxing out at 30 seconds after successive failures.

Retries continue indefinitely by default. To limit retries, set the `maxRetries` parameter in the `startSingleTimer` method.

## Deleting a timer

Let’s review the implementation of the confirmation endpoint.

**{sample-base-url}/reliable-timers/src/main/java/com/example/api/OrderEndpoint.java[OrderEndpoint.java]**


```
1. Confirms the order via `OrderEntity`.
2. Removes the timer upon successful confirmation.

Once `OrderEntity` completes the operation, the timer is canceled. This sequence is important. Even if canceling the timer fails, the `OrderEntity.cancel` method, as seen earlier, ensures proper handling for obsolete timers, signaling Akka that they can be removed.

**💡 TIP**\
You could entirely skip timer cancellation when handling confirmation. In this case, the registered timer would be triggered later, and `OrderEntity.cancel` would handle this case gracefully. However, it’s always good practice to perform housekeeping to save resources.

## Best practices

When a timer is scheduled, the component method call is serialized and stored. The serialized data includes the component id, method name, and method parameter. Therefore, method signatures must remain stable across deployments.

A timer will fail to execute if any of the following conditions occur:

* The component id changes, preventing the timer from locating the component.
* The method name changes, causing the timer to miss the correct method to call.
* The payload format changes, leading to deserialization errors for the payload.

If any of these changes happen in a new deployment, the timer becomes broken. This means the timer will repeatedly fail to execute and will be rescheduled indefinitely. Only a compatible deployment restoring the component will allow the timer to function correctly.

If you need to refactor a method used by a timer, it’s recommended to keep the old method and delegate calls to the updated method.

For example, suppose `OrderTimedAction` had a legacy method called `expire` that took `ExpireOrder` as a parameter.

**{sample-base-url}/reliable-timers/src/main/java/com/example/application/OrderTimedAction.java[OrderTimedAction.java]**


```

In this case, keeping the legacy method and delegating to the new `OrderTimedAction.expireOrder` method ensures compatibility. 

Alternatively, if the legacy method is no longer needed, you can implement a no-operation method by returning `effects.done()`.

**{sample-base-url}/reliable-timers/src/main/java/com/example/application/OrderTimedAction.java[OrderTimedAction.java]**


```

Retain the legacy method for as long as you have scheduled calls referring to it.

To view scheduled timers in your service, use the following CLI command:

```command line
akka services components list-timers reliable-timers -o json  # ①
```

1. Replace 'reliable-timers' with your service name.

This command outputs a list of scheduled timers in JSON format.
