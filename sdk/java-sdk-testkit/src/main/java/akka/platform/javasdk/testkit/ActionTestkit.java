/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit;

import akka.platform.javasdk.Metadata;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.action.ActionCreationContext;
import akka.platform.javasdk.testkit.impl.ActionResultImpl;
import akka.platform.javasdk.testkit.impl.TestKitActionContext;

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

  private final Function<ActionCreationContext, A> actionFactory;

  private ActionTestkit(Function<ActionCreationContext, A> actionFactory) {
    this.actionFactory = actionFactory;
  }

  public static <A extends Action> ActionTestkit<A> of(
      Function<ActionCreationContext, A> actionFactory) {
    return new ActionTestkit<>(actionFactory);
  }

  public static <A extends Action> ActionTestkit<A> of(Supplier<A> actionFactory) {
    return new ActionTestkit<>(ctx -> actionFactory.get());
  }

  private A createAction(TestKitActionContext context) {
    A action = actionFactory.apply(context);
    action._internalSetActionContext(Optional.of(context));
    return action;
  }

  /**
   * The {@code call} method can be used to simulate a unary call to the Action. The passed java lambda should
   * return an Action.Effect. The Effect is interpreted into an ActionResult that can be used in
   * test assertions.
   *
   * @param func A function from Action to Action.Effect
   * @return an ActionResult
   * @param <R> The type of reply that is expected from invoking a command handler
   */
  public <R> ActionResult<R> call(Function<A, Action.Effect<R>> func) {
    return call(func, Metadata.EMPTY);
  }

  /**
   * The {@code call} method can be used to simulate a unary call to the Action. The passed java lambda should
   * return an Action.Effect. The Effect is interpreted into an ActionResult that can be used in
   * test assertions.
   *
   * @param func     A function from Action to Action.Effect
   * @param metadata A metadata passed as a call context
   * @param <R>      The type of reply that is expected from invoking a command handler
   * @return an ActionResult
   */
  public <R> ActionResult<R> call(Function<A, Action.Effect<R>> func, Metadata metadata) {
    TestKitActionContext context = new TestKitActionContext(metadata, MockRegistry.EMPTY);
    return new ActionResultImpl<>(func.apply(createAction(context)));
  }

}
