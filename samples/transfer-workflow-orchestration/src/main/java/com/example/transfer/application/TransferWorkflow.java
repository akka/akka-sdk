package com.example.transfer.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import com.example.transfer.application.FraudDetectionService.FraudDetectionResult;
import com.example.transfer.domain.Transfer;
import com.example.transfer.domain.TransferState;

import static akka.Done.done;
import static com.example.transfer.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.TRANSFER_ACCEPTANCE_TIMED_OUT;
import static com.example.transfer.domain.TransferState.TransferStatus.WAITING_FOR_ACCEPTANCE;
import static com.example.transfer.domain.TransferState.TransferStatus.WITHDRAW_SUCCEEDED;
import static java.time.Duration.ofHours;

/**
 * Workflow for handling transfers between wallets. It includes human-in-the-loop aspect for accepting transfers that exceed a certain amount.
 *
 * For other aspects like error handling, retry strategy, compensation @see <a href="https://doc.akka.io/java/workflows.html#_error_handling">documentation</a>.
 */
@ComponentId("transfer-workflow")
public class TransferWorkflow extends Workflow<TransferState> {

  private final WalletService walletService;
  private final FraudDetectionService fraudDetectionService;
  private final ComponentClient componentClient;
  private final String transferId;

  public TransferWorkflow(WalletService walletService, FraudDetectionService fraudDetectionService, ComponentClient componentClient, WorkflowContext workflowContext) {
    this.walletService = walletService;
    this.fraudDetectionService = fraudDetectionService;
    this.componentClient = componentClient;
    this.transferId = workflowContext.workflowId();
  }

  @Override
  public WorkflowDef<TransferState> definition() {
    return workflow()
      .addStep(detectFraudsStep())
      .addStep(waitForAcceptanceStep())
      .addStep(withdrawStep())
      .addStep(depositStep());
  }

  private Step detectFraudsStep() {
    return step("detect-frauds")
      .call(() -> fraudDetectionService.check(currentState().transfer()))
      .andThen(FraudDetectionResult.class, result ->
        switch (result) {
          case ACCEPTED -> effects().transitionTo("withdraw");
          case MANUAL_ACCEPTANCE_REQUIRED -> effects()
            .updateState(currentState().withStatus(WAITING_FOR_ACCEPTANCE))
            .transitionTo("wait-for-acceptance");
        });
  }

  private Step withdrawStep() {
    return step("withdraw")
      .call(() -> {
        var fromWalletId = currentState().transfer().from();
        var amount = currentState().transfer().amount();
        walletService.withdraw(fromWalletId, amount);
      })
      .andThen(() -> effects()
        .updateState(currentState().withStatus(WITHDRAW_SUCCEEDED))
        .transitionTo("deposit"));
  }

  private Step depositStep() {
    return step("deposit")
      .call(() -> {
        var to = currentState().transfer().to();
        var amount = currentState().transfer().amount();
        walletService.deposit(to, amount);
      })
      .andThen(() -> effects()
        .updateState(currentState().withStatus(COMPLETED))
        .end());
  }

  public Effect<String> start(Transfer transfer) {
    return effects()
      .updateState(TransferState.create(transferId, transfer))
      .transitionTo("detect-frauds")
      .thenReply("transfer started");
  }

  private Step waitForAcceptanceStep() {
    return step("wait-for-acceptance")
      .call(() -> {
        timers().createSingleTimer(
          "acceptanceTimeout-" + transferId,
          ofHours(8),
          componentClient.forWorkflow(transferId)
            .method(TransferWorkflow::acceptanceTimeout)
            .deferred());
      })
      .andThen(() -> effects().pause());
  }

  public Effect<Done> accept() {
    if (currentState().status().equals(WAITING_FOR_ACCEPTANCE)) {
      return effects()
        .transitionTo("withdraw")
        .thenReply(done());
    }else {
      return effects().error("Acceptance not allowed in current state: " + currentState().status());
    }
  }

  public Effect<Done> acceptanceTimeout() {
    if (currentState().status().equals(WAITING_FOR_ACCEPTANCE)) {
      return effects()
        .updateState(currentState().withStatus(TRANSFER_ACCEPTANCE_TIMED_OUT))
        .end()
        .thenReply(done());
    }else {
      return effects().reply(done());
    }
  }

  public Effect<TransferState> get() {
    return effects()
      .reply(currentState());
  }
}
