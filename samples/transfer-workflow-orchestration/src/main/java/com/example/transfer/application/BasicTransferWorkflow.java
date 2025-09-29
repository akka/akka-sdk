package com.example.transfer.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.workflow.Workflow;
import com.example.transfer.domain.Transfer;

/**
 * The most basic transfer workflow that handles a transfer. Illustrates the basic structure of a
 * workflow and how to orchestrate external services calls.
 * <p>
 * For a more advanced example, see {@link TransferWorkflow}.
 */
@Component(id = "basic-transfer-workflow")
public class BasicTransferWorkflow extends Workflow<Transfer> {

  private final WalletService accountService;

  public BasicTransferWorkflow(WalletService walletService) {
    this.accountService = walletService;
  }

  @StepName("withdraw")
  private StepEffect withdrawStep() {
    accountService.withdraw(currentState().from(), currentState().amount());

    return stepEffects().thenTransitionTo(BasicTransferWorkflow::depositStep);
  }

  @StepName("deposit")
  private StepEffect depositStep() {
    accountService.deposit(currentState().to(), currentState().amount());
    return stepEffects().thenEnd();
  }

  public Effect<String> start(Transfer transfer) {
    return effects()
      .updateState(transfer)
      .transitionTo(BasicTransferWorkflow::withdrawStep)
      .thenReply("transfer started");
  }
}
