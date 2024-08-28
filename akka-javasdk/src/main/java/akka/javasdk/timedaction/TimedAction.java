/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.timedaction;

import akka.Done;
import akka.javasdk.impl.action.CommandContextImpl;
import akka.javasdk.impl.timedaction.TimedActionEffectImpl;
import akka.javasdk.timer.TimerScheduler;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * TimedAction is stateless component that can be used together with a Timer to schedule an action.
 *
 * An TimedAction method should return an {@link Effect} that describes the result of the action invocation.
 */
public abstract class TimedAction {

  private volatile Optional<CommandContext> commandContext = Optional.empty();

  /**
   * Additional context and metadata for a message handler.
   *
   * <p>It will throw an exception if accessed from constructor.
   */
  protected final CommandContext commandContext() {
    return commandContext("CommandContext is only available when handling a command.");
  }

  private CommandContext commandContext(String errorMessage) {
    return commandContext.orElseThrow(() -> new IllegalStateException(errorMessage));
  }

  /**
   * INTERNAL API
   */
  public void _internalSetCommandContext(Optional<CommandContext> context) {
    commandContext = context;
  }

  public final Effect.Builder effects() {
    return TimedActionEffectImpl.builder();
  }

  /**
   * Returns a {@link TimerScheduler} that can be used to schedule further in time.
   */
  public final TimerScheduler timers() {
    CommandContextImpl impl =
      (CommandContextImpl)
        commandContext("Timers can only be scheduled or cancelled when handling a command.");
    return impl.timers();
  }

  /**
   * An Effect is a description of what Kalix needs to do after the command is handled.
   * You can think of it as a set of instructions you are passing to Kalix. Kalix will process the instructions on your
   * behalf.
   * <p>
   * Each Kalix component defines its own effects, which are a set of predefined
   * operations that match the capabilities of that component.
   * <p>
   * An TimedAction Effect can either:
   * <p>
   * <ul>
   *   <li>reply with Done to confirm that the command was processed successfully
   *   <li>return an error
   * </ul>
   */
  public interface Effect {

    /**
     * Construct the effect that is returned by the command handler. The effect describes next
     * processing actions, such as sending a reply.
     */
    interface Builder {

      /**
       * Command was processed successfully.
       */
      Effect done();

      /**
       * Command was processed successfully from an async operation result
       */
      Effect asyncDone(CompletionStage<Done> message);


      /**
       * Create an error reply.
       *
       * @param description The description of the error.
       * @return An error reply.
       */
      Effect error(String description);

      /**
       * Create a reply from an async operation result returning an effect.
       *
       * @param futureEffect The future effect to reply with.
       * @return A reply, the actual type depends on the nested Effect.
       */
      Effect asyncEffect(CompletionStage<Effect> futureEffect);
    }
  }
}
