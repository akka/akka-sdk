/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.actions.echo.Message;
import akkajavasdk.components.workflowentities.FraudDetectionResult.TransferRejected;
import akkajavasdk.components.workflowentities.FraudDetectionResult.TransferRequiresManualAcceptation;
import akkajavasdk.components.workflowentities.FraudDetectionResult.TransferVerified;

@ComponentId("transfer-workflow-with-fraud-detection")
public class TransferWorkflowWithFraudDetection extends Workflow<TransferState> {

  private final ComponentClient componentClient;

  public TransferWorkflowWithFraudDetection(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }


  public Effect<Message> startTransfer(Transfer transfer) {
    if (transfer.amount() <= 0) {
      return effects().error("Transfer amount should be greater than zero");
    } else {
      if (currentState() == null) {
        return effects()
            .updateState(new TransferState(transfer, "started"))
            .transitionTo(TransferWorkflowWithFraudDetection::detectFraud)
            .withInput(transfer)
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
          .transitionTo(TransferWorkflowWithFraudDetection::withdraw)
          .withInput(withdrawInput)
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

  private FraudDetectionResult checkFrauds(Transfer transfer) {
    if (transfer.amount() >= 1000 && transfer.amount() < 1000000) {
      return new TransferRequiresManualAcceptation(transfer);
    } else if (transfer.amount() >= 1000000) {
      return new TransferRejected(transfer);
    } else {
      return new TransferVerified(transfer);
    }
  }

  private StepEffect detectFraud(Transfer transfer) {

    var result = checkFrauds(transfer);
    var state = currentState().withLastStep("fraud-detection");

    switch (result) {
      case TransferVerified transferVerified -> {
        var withdrawInput = new Withdraw(transferVerified.transfer.from(), transferVerified.transfer.amount());

        return stepEffects()
          .updateState(state)
          .thenTransitionTo(TransferWorkflowWithFraudDetection::withdraw)
          .withInput(withdrawInput);
      }
      case TransferRequiresManualAcceptation __ -> {
        return stepEffects()
          .updateState(state)
          .thenPause();
      }
      case TransferRejected __ -> {
        return stepEffects()
          .updateState(state.asFinished())
          .thenEnd();
      }
    }
  }

  private StepEffect withdraw(Withdraw withdraw) {

    componentClient
      .forKeyValueEntity(withdraw.from)
      .method(WalletEntity::withdraw)
      .invoke(withdraw.amount);

    var state = currentState().withLastStep("withdraw");
    var depositInput = new Deposit(currentState().transfer().to(), currentState().transfer().amount());

    return stepEffects()
      .updateState(state)
      .thenTransitionTo(TransferWorkflowWithFraudDetection::deposit)
      .withInput(depositInput);
  }

  private StepEffect deposit(Deposit deposit) {

    componentClient
      .forKeyValueEntity(deposit.to)
      .method(WalletEntity::deposit)
      .invoke(deposit.amount);

    var state = currentState().withLastStep("deposit");

    return stepEffects()
      .updateState(state)
      .thenEnd();
  }
}
