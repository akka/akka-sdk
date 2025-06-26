/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.Metadata;
import akka.javasdk.testkit.impl.TestKitCommandContextTimed;
import akka.javasdk.testkit.impl.TimedActionResultImpl;
import akka.javasdk.timedaction.TimedAction;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * TimedAction Testkit for use in unit tests for TimedActions.
 *
 * <p>To test a TimedAction create a testkit instance by calling one of the available {@code
 * TimedActionTestkit.of} methods. The returned testkit can be used as many times as you want. It doesn't
 * preserve any state between invocations.
 *
 * <p>Use the {@code call or stream} methods to interact with the testkit.
 */
public class TimedActionTestkit<A extends TimedAction> {

  private final Supplier<A> actionFactory;

  private TimedActionTestkit(Supplier<A> actionFactory) {
    this.actionFactory = actionFactory;
  }

  public static <A extends TimedAction> TimedActionTestkit<A> of(
      Supplier<A> actionFactory) {
    return new TimedActionTestkit<>(actionFactory);
  }

  private A createTimedAction(TestKitCommandContextTimed context) {
    A action = actionFactory.get();
    action._internalSetCommandContext(Optional.of(context));
    return action;
  }

  /**
   * The {@code call} method can be used to simulate a unary call to the Action. The passed java lambda should
   * return an Action.Effect. The Effect is interpreted into an ActionResult that can be used in
   * test assertions.
   *
   * @param func A function from Action to Action.Effect
   * @return an ActionResult
   */
  public TimedActionResult call(Function<A, TimedAction.Effect> func) {
    return call(func, Metadata.EMPTY);
  }

  /**
   * The {@code call} method can be used to simulate a unary call to the Action. The passed java lambda should
   * return an Action.Effect. The Effect is interpreted into an ActionResult that can be used in
   * test assertions.
   *
   * @param func     A function from Action to Action.Effect
   * @param metadata A metadata passed as a call context
   * @return an ActionResult
   */
  public TimedActionResult call(Function<A, TimedAction.Effect> func, Metadata metadata) {
    TestKitCommandContextTimed context = new TestKitCommandContextTimed(metadata, MockRegistry.EMPTY);
    return new TimedActionResultImpl<>(func.apply(createTimedAction(context)));
  }

}
