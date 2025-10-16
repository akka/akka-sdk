/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static akka.javasdk.testkit.EntitySerializationChecker.verifySerDerWithExpectedType;

import akka.javasdk.Metadata;
import akka.javasdk.impl.client.MethodRefResolver;
import akka.javasdk.testkit.impl.TestKitCommandContextTimed;
import akka.javasdk.testkit.impl.TimedActionResultImpl;
import akka.javasdk.timedaction.TimedAction;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * TimedAction Testkit for use in unit tests for TimedActions.
 *
 * <p>To test a TimedAction create a testkit instance by calling one of the available {@code
 * TimedActionTestkit.of} methods. The returned testkit can be used as many times as you want. It
 * doesn't preserve any state between invocations.
 *
 * <p>Use the {@code call or stream} methods to interact with the testkit.
 */
public class TimedActionTestkit<A extends TimedAction> {

  private final Supplier<A> actionFactory;

  private TimedActionTestkit(Supplier<A> actionFactory) {
    this.actionFactory = actionFactory;
  }

  public static <A extends TimedAction> TimedActionTestkit<A> of(Supplier<A> actionFactory) {
    return new TimedActionTestkit<>(actionFactory);
  }

  public final class MethodRef {
    private final akka.japi.function.Function<A, TimedAction.Effect> func;
    private final Metadata metadata;

    public MethodRef(akka.japi.function.Function<A, TimedAction.Effect> func, Metadata metadata) {
      this.func = func;
      this.metadata = metadata;
    }

    public MethodRef withMetadata(Metadata metadata) {
      return new MethodRef(func, metadata);
    }

    public TimedActionResult invoke() {
      return TimedActionTestkit.this.call(func, metadata);
    }
  }

  public final class MethodRef1<I> {
    private final akka.japi.function.Function2<A, I, TimedAction.Effect> func;
    private final Metadata metadata;

    public MethodRef1(
        akka.japi.function.Function2<A, I, TimedAction.Effect> func, Metadata metadata) {
      this.func = func;
      this.metadata = metadata;
    }

    public MethodRef1<I> withMetadata(Metadata metadata) {
      return new MethodRef1<>(func, metadata);
    }

    public TimedActionResult invoke(I input) {
      var method = MethodRefResolver.resolveMethodRef(func);
      var inputType = method.getParameterTypes()[0];
      A action = actionFactory.get();

      verifySerDerWithExpectedType(inputType, input, action);
      return TimedActionTestkit.this.call(
          kve -> {
            try {
              return func.apply(kve, input);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          },
          metadata);
    }
  }

  private A createTimedAction(TestKitCommandContextTimed context) {
    A action = actionFactory.get();
    action._internalSetCommandContext(Optional.of(context));
    return action;
  }

  /**
   * The {@code call} method can be used to simulate a unary call to the Action. The passed java
   * lambda should return an Action.Effect. The Effect is interpreted into an ActionResult that can
   * be used in test assertions.
   *
   * @param func A function from Action to Action.Effect
   * @return an ActionResult
   * @deprecated Use "method(MyTimedAction::myCommandHandler).invoke()" instead
   */
  @Deprecated(since = "3.5.5", forRemoval = true)
  public TimedActionResult call(akka.japi.function.Function<A, TimedAction.Effect> func) {
    return call(func, Metadata.EMPTY);
  }

  /**
   * The {@code call} method can be used to simulate a unary call to the Action. The passed java
   * lambda should return an Action.Effect. The Effect is interpreted into an ActionResult that can
   * be used in test assertions.
   *
   * @param func A function from Action to Action.Effect
   * @param metadata A metadata passed as a call context
   * @return an ActionResult
   * @deprecated Use "method(MyTimedAction::myCommandHandler).withMetadata(metadata).invoke()"
   *     instead
   */
  @Deprecated(since = "3.5.5", forRemoval = true)
  public TimedActionResult call(
      akka.japi.function.Function<A, TimedAction.Effect> func, Metadata metadata) {
    TestKitCommandContextTimed context =
        new TestKitCommandContextTimed(metadata, MockRegistry.EMPTY);
    try {
      TimedAction.Effect effect = func.apply(createTimedAction(context));
      return new TimedActionResultImpl<>(effect);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Pass in a Timed Action command handler method reference without parameters, e.g. {@code
   * MyTimedAction::myCommandHandler}
   */
  public MethodRef method(akka.japi.function.Function<A, TimedAction.Effect> func) {
    return new MethodRef(func, Metadata.EMPTY);
  }

  /**
   * Pass in a Timed Action command handler method reference with a single parameter, e.g. {@code
   * MyTimedAction::myCommandHandler}
   */
  public <I> MethodRef1<I> method(akka.japi.function.Function2<A, I, TimedAction.Effect> func) {
    return new MethodRef1<>(func, Metadata.EMPTY);
  }
}
