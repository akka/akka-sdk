/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.CommandException;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.MyException;
import akkajavasdk.components.actions.echo.Message;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("transfer-workflow")
public class TransferWorkflow extends Workflow<TransferState> {

  private final String withdrawStepName = "withdraw";

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ComponentClient componentClient;

  private boolean constructedOnVt = Thread.currentThread().isVirtual();

  public TransferWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowConfig configuration() {
    return WorkflowConfig
      .builder()
      .workflowTimeout(Duration.ofSeconds(10))
      .build();
  }

  public Effect<Message> startTransfer(Transfer transfer) {
    if (transfer.amount() <= 0.0) {
      return effects().reply(new Message("Transfer amount should be greater than zero"));
    } else {
      if (currentState() == null) {
        return effects()
            .updateState(new TransferState(transfer, "started"))
            .transitionTo(TransferWorkflow::withdraw)
            .withInput(new Withdraw(transfer.from(), transfer.amount()))
            .thenReply(new Message("transfer started"));
      } else {
        return effects().reply(new Message("transfer started already"));
      }
    }
  }

  @StepName("withdraw-step")
  private StepEffect withdraw(Withdraw withdraw) {

    componentClient
      .forKeyValueEntity(withdraw.from)
      .method(WalletEntity::withdraw)
      .invoke(withdraw.amount);

    var state = currentState().withLastStep("withdrawn").asAccepted();
    return stepEffects()
        .updateState(state)
        .thenTransitionTo(TransferWorkflow::deposit)
        .withInput(new Deposit(currentState().transfer().to(), currentState().transfer().amount()));
  }


  @StepName("deposit-step")
  private StepEffect deposit(Deposit deposit) {

    componentClient
        .forKeyValueEntity(deposit.to)
        .method(WalletEntity::deposit)
        .invoke(deposit.amount);

    var state = currentState().withLastStep("deposited").asFinished();
    return stepEffects()
      .updateState(state)
      .thenTransitionTo(TransferWorkflow::logAndStop);
  }

  private StepEffect logAndStop() {
    logger.info("Workflow finished");
    var state = currentState().withLastStep("logAndStop");
    return stepEffects().updateState(state).thenEnd();
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

  public record SomeClass(String someValue) {}

  public Effect<Message> genericCall(List<SomeClass> objects) {
    return effects().reply(new Message("genericCall ok"));
  }

  public Effect<String> run(String errorType) {
    if ("errorMessage".equals(errorType)) {
      return effects().error(errorType);
    } else if ("errorCommandException".equals(errorType)) {
      return effects().error(new CommandException(errorType));
    } else if ("errorMyException".equals(errorType)) {
      return effects().error(new MyException(errorType, new MyException.SomeData("some data")));
    } else if ("throwMyException".equals(errorType)) {
      throw new MyException(errorType, new MyException.SomeData("some data"));
    } else if ("throwRuntimeException".equals(errorType)) {
      throw new RuntimeException(errorType);
    } else {
      return effects().reply("No error triggered for: " + errorType);
    }
  }
}
