/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.legacy;

import akkajavasdk.components.actions.echo.Message;
import akkajavasdk.components.workflowentities.Deposit;
import akkajavasdk.components.workflowentities.FraudDetectionResult;
import akkajavasdk.components.workflowentities.FraudDetectionResult.TransferRejected;
import akkajavasdk.components.workflowentities.FraudDetectionResult.TransferRequiresManualAcceptation;
import akkajavasdk.components.workflowentities.FraudDetectionResult.TransferVerified;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.workflowentities.Transfer;
import akkajavasdk.components.workflowentities.TransferState;
import akkajavasdk.components.workflowentities.WalletEntity;
import akkajavasdk.components.workflowentities.Withdraw;

@ComponentId("transfer-workflow-with-fraud-detection-legacy")
public class TransferWorkflowWithFraudDetection extends Workflow<TransferState> {

  private final String fraudDetectionStepName = "fraud-detection";
  private final String withdrawStepName = "withdraw";
  private final String depositStepName = "deposit";

  private ComponentClient componentClient;

  public TransferWorkflowWithFraudDetection(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowDef<TransferState> definition() {
    var fraudDetection =
      step(fraudDetectionStepName)
        .call(Transfer.class, this::checkFrauds)
        .andThen(FraudDetectionResult.class, this::processFraudDetectionResult);

    var withdraw =
      step(withdrawStepName)
        .call(Withdraw.class, cmd ->
          componentClient.forKeyValueEntity(cmd.from).method(WalletEntity::withdraw).invoke(cmd.amount))
        .andThen(String.class, this::moveToDeposit);

    var deposit =
      step(depositStepName)
        .call(Deposit.class, cmd -> componentClient.forKeyValueEntity(cmd.to).method(WalletEntity::deposit).invoke(cmd.amount))
        .andThen(String.class, this::finishWithSuccess);

    return workflow()
      .addStep(fraudDetection)
      .addStep(withdraw)
      .addStep(deposit);
  }

  public Effect<Message> startTransfer(Transfer transfer) {
    if (transfer.amount() <= 0) {
      return effects().error("Transfer amount should be greater than zero");
    } else {
      if (currentState() == null) {
        return effects()
          .updateState(new TransferState(transfer, "started"))
          .transitionTo(fraudDetectionStepName, transfer)
          .thenReply(new Message("transfer started"));
      } else {
        return effects().reply(new Message("transfer started already"));
      }
    }
  }

  public Effect<Message> acceptTransfer() {
    if (currentState() == null) {
      return effects().reply(new Message("transfer not started"));
    } else if (!currentState().accepted() && !currentState().finished()) {
      var withdrawInput = new Withdraw(currentState().transfer().from(), currentState().transfer().amount());
      return effects()
        .updateState(currentState().asAccepted())
        .transitionTo(withdrawStepName, withdrawInput)
        .thenReply(new Message("transfer accepted"));
    } else {
      return effects().reply(new Message("transfer cannot be accepted"));
    }
  }

  public Effect<TransferState> getTransferState() {
    if (currentState() == null) {
      return effects().error("transfer not started");
    } else {
      return effects().reply(currentState());
    }
  }

  private Effect.TransitionalEffect<Void> finishWithSuccess(String response) {
    var state = currentState().withLastStep(depositStepName);
    return effects().updateState(state).end();
  }

  private Effect.TransitionalEffect<Void> moveToDeposit(String response) {
    var state = currentState().withLastStep(withdrawStepName);

    var depositInput = new Deposit(currentState().transfer().to(), currentState().transfer().amount());

    return effects()
      .updateState(state)
      .transitionTo(depositStepName, depositInput);
  }

  private FraudDetectionResult checkFrauds(Transfer transfer) {
    if (transfer.amount() >= 1000 && transfer.amount() < 1000000) {
      return new TransferRequiresManualAcceptation(transfer);
    } else if (transfer.amount() >= 1000000) {
      return new TransferRejected(transfer);
    } else {
      return new TransferVerified(transfer);
    }
  }

  private Effect.TransitionalEffect<Void> processFraudDetectionResult(FraudDetectionResult result) {
    var state = currentState().withLastStep(fraudDetectionStepName);

    switch (result) {
      case TransferVerified transferVerified -> {
        var withdrawInput = new Withdraw(transferVerified.transfer.from(), transferVerified.transfer.amount());

        return effects()
          .updateState(state)
          .transitionTo(withdrawStepName, withdrawInput);
      }
      case TransferRequiresManualAcceptation transferRequiresManualAcceptation -> {
        return effects()
          .updateState(state)
          .pause();
      }
      case TransferRejected transferRejected -> {
        return effects()
          .updateState(state.asFinished())
          .end();
      }
    }
  }
}
