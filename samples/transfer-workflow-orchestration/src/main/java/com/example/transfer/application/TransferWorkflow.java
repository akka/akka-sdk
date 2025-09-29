package com.example.transfer.application;

import static akka.Done.done;
import static com.example.transfer.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.TRANSFER_ACCEPTANCE_TIMED_OUT;
import static com.example.transfer.domain.TransferState.TransferStatus.WAITING_FOR_ACCEPTANCE;
import static com.example.transfer.domain.TransferState.TransferStatus.WITHDRAW_SUCCEEDED;
import static java.time.Duration.ofHours;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import com.example.transfer.domain.Transfer;
import com.example.transfer.domain.TransferState;

/**
 * Workflow for handling transfers between wallets. It includes human-in-the-loop aspect for
 * accepting transfers that exceed a certain amount.
 * <p>
 * For other aspects like error handling, retry strategy, compensation
 * @see <a href="https://doc.akka.io/java/workflows.html#_error_handling">documentation</a>.
 */
@Component(id = "transfer-workflow")
public class TransferWorkflow extends Workflow<TransferState> {

  private final WalletService walletService;
  private final FraudDetectionService fraudDetectionService;
  private final ComponentClient componentClient;
  private final String transferId;

  public TransferWorkflow(
    WalletService walletService,
    FraudDetectionService fraudDetectionService,
    ComponentClient componentClient,
    WorkflowContext workflowContext
  ) {
    this.walletService = walletService;
    this.fraudDetectionService = fraudDetectionService;
    this.componentClient = componentClient;
    this.transferId = workflowContext.workflowId();
  }

  public Effect<String> start(Transfer transfer) {
    return effects()
      .updateState(TransferState.create(transferId, transfer))
      .transitionTo(TransferWorkflow::detectFraudsStep)
      .thenReply("transfer started");
  }

  @StepName("detect-frauds")
  private StepEffect detectFraudsStep() {
    var result = fraudDetectionService.check(currentState().transfer());

    return switch (result) {
      case ACCEPTED -> stepEffects().thenTransitionTo(TransferWorkflow::withdrawStep);
      case MANUAL_ACCEPTANCE_REQUIRED -> stepEffects()
        .updateState(currentState().withStatus(WAITING_FOR_ACCEPTANCE))
        .thenTransitionTo(TransferWorkflow::waitForAcceptanceStep);
    };
  }

  @StepName("wait-for-acceptance")
  private StepEffect waitForAcceptanceStep() {
    timers()
      .createSingleTimer(
        "acceptanceTimeout-" + transferId,
        ofHours(8),
        componentClient
          .forWorkflow(transferId)
          .method(TransferWorkflow::acceptanceTimeout)
          .deferred()
      );

    return stepEffects().thenPause();
  }

  @StepName("withdraw")
  private StepEffect withdrawStep() {
    var fromWalletId = currentState().transfer().from();
    var amount = currentState().transfer().amount();
    walletService.withdraw(fromWalletId, amount);

    return stepEffects()
      .updateState(currentState().withStatus(WITHDRAW_SUCCEEDED))
      .thenTransitionTo(TransferWorkflow::depositStep);
  }

  @StepName("deposit")
  private StepEffect depositStep() {
    var to = currentState().transfer().to();
    var amount = currentState().transfer().amount();
    walletService.deposit(to, amount);

    return stepEffects().updateState(currentState().withStatus(COMPLETED)).thenEnd();
  }

  public Effect<Done> accept() {
    if (currentState().status().equals(WAITING_FOR_ACCEPTANCE)) {
      return effects().transitionTo(TransferWorkflow::withdrawStep).thenReply(done());
    } else {
      return effects()
        .error("Acceptance not allowed in current state: " + currentState().status());
    }
  }

  public Effect<Done> acceptanceTimeout() {
    if (currentState().status().equals(WAITING_FOR_ACCEPTANCE)) {
      return effects()
        .updateState(currentState().withStatus(TRANSFER_ACCEPTANCE_TIMED_OUT))
        .end()
        .thenReply(done());
    } else {
      return effects().reply(done());
    }
  }

  public Effect<TransferState> get() {
    return effects().reply(currentState());
  }
}
