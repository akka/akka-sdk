/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.actions.echo.Message;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("transfer-workflow")
public class TransferWorkflow extends Workflow<TransferState> {

  private final String withdrawStepName = "withdraw";
  private final String depositStepName = "deposit";

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ComponentClient componentClient;

  private boolean constructedOnVt = Thread.currentThread().isVirtual();

  public TransferWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public Settings settings() {
    return settingsBuilder()
      .timeout(Duration.ofSeconds(10))
      .build();
  }

  public Effect<Message> startTransfer(Transfer transfer) {
    if (transfer.amount() <= 0.0) {
      return effects().reply(new Message("Transfer amount should be greater than zero"));
    } else {
      if (currentState() == null) {
        return effects()
          .updateState(new TransferState(transfer, "started"))
          .transitionTo(withdrawStepName, new Withdraw(transfer.from(), transfer.amount()))
          .thenReply(new Message("transfer started"));
      } else {
        return effects().reply(new Message("transfer started already"));
      }
    }
  }

  private Step withdraw() {
    return step(withdrawStepName)
        .call(
            Withdraw.class,
            cmd ->
                componentClient
                    .forKeyValueEntity(cmd.from)
                    .method(WalletEntity::withdraw)
                    .invoke(cmd.amount))
        .andThen(
            () -> {
              var state = currentState().withLastStep("withdrawn").asAccepted();

              var depositInput =
                  new Deposit(currentState().transfer().to(), currentState().transfer().amount());

              return effects().updateState(state).transitionTo(depositStepName, depositInput);
            });
  }

  private Step deposit() {
    return step(depositStepName)
        .call(
            Deposit.class,
            cmd ->
                componentClient
                    .forKeyValueEntity(cmd.to)
                    .method(WalletEntity::deposit)
                    .invoke(cmd.amount))
        .andThen(
            () -> {
              var state = currentState().withLastStep("deposited").asFinished();
              return effects().updateState(state).transitionTo("logAndStop");
            });
  }

  private Step logAndStop() {
    // this last step is mainly to ensure that Runnables are properly supported
    return step("logAndStop")
        .call(() -> logger.info("Workflow finished"))
        .andThen(
            () -> {
              var state = currentState().withLastStep("logAndStop");
              return effects().updateState(state).end();
            });
  }

  public Effect<Done> updateAndDelete(Transfer transfer) {
    return effects()
      .updateState(new TransferState(transfer, "startedAndDeleted"))
      .delete()
      .thenReply(done());
  }

  public Effect<Boolean> commandHandlerIsOnVirtualThread() {
    return effects().reply(Thread.currentThread().isVirtual() && constructedOnVt);
  }

  public Effect<Message> genericStringsCall(List<String> primitives) {
    return effects().reply(new Message("genericCall ok"));
  }


  public Effect<Message> getLastStep() {
    return effects().reply(new Message(currentState().lastStep()));
  }

  public Effect<TransferState> get() {
    if (currentState() == null && isDeleted()) {
      return effects().reply(TransferState.EMPTY);
    }
    return effects().reply(currentState());
  }

  public Effect<Done> delete() {
    return effects().delete().thenReply(done());
  }

  public Effect<Boolean> hasBeenDeleted() {
    return effects().reply(isDeleted());
  }

  public record SomeClass(String someValue) {
  }

  public Effect<Message> genericCall(List<SomeClass> objects) {
    return effects().reply(new Message("genericCall ok"));
  }
}
