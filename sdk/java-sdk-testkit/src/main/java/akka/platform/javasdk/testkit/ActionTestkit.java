/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit;

import akka.platform.javasdk.Metadata;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.action.ActionContext;
import akka.platform.javasdk.testkit.impl.ActionResultImpl;
import akka.platform.javasdk.testkit.impl.TestKitMessageContext;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Action Testkit for use in unit tests for Actions.
 *
 * <p>To test an Action create a testkit instance by calling one of the available {@code
 * ActionTestkit.of} methods. The returned testkit can be used as many times as you want. It doesn't
 * preserve any state between invocations.
 *
 * <p>Use the {@code call or stream} methods to interact with the testkit.
 */
public class ActionTestkit<A extends Action> {

  private final Function<ActionContext, A> actionFactory;

  private ActionTestkit(Function<ActionContext, A> actionFactory) {
    this.actionFactory = actionFactory;
  }

  public static <A extends Action> ActionTestkit<A> of(
      Function<ActionContext, A> actionFactory) {
    return new ActionTestkit<>(actionFactory);
  }

  public static <A extends Action> ActionTestkit<A> of(Supplier<A> actionFactory) {
    return new ActionTestkit<>(ctx -> actionFactory.get());
  }

  private A createAction(TestKitMessageContext context) {
    A action = actionFactory.apply(context);
    action._internalSetMessageContext(Optional.of(context));
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
  public ActionResult call(Function<A, Action.Effect> func) {
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
  public ActionResult call(Function<A, Action.Effect> func, Metadata metadata) {
    TestKitMessageContext context = new TestKitMessageContext(metadata, MockRegistry.EMPTY);
    return new ActionResultImpl<>(func.apply(createAction(context)));
  }

}
