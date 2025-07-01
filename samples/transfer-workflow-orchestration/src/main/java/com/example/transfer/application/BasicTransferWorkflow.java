package com.example.transfer.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;
import com.example.transfer.domain.Transfer;

/**
 * The most basic transfer workflow that handles a transfer. Illustrates the basic structure of a workflow
 * and how to orchestrate external services calls.
 *
 * For a more advanced example, see {@link TransferWorkflow}.
 */
@ComponentId("basic-transfer-workflow")
public class BasicTransferWorkflow extends Workflow<Transfer> {

  private final WalletService accountService;

  public BasicTransferWorkflow(WalletService walletService) {
    this.accountService = walletService;
  }

  @Override
  public WorkflowDef<Transfer> definition() {
    return workflow()
      .addStep(withdrawStep())
      .addStep(depositStep());
  }

  private Step withdrawStep() {
    return step("withdraw")
      .call(() -> accountService.withdraw(currentState().from(), currentState().amount()))
      .andThen(() -> effects().transitionTo("deposit"));
  }

  private Step depositStep() {
    return step("deposit")
      .call(() -> accountService.deposit(currentState().to(), currentState().amount()))
      .andThen(() -> effects().end());
  }

  public Effect<String> start(Transfer transfer) {
    return effects()
      .updateState(transfer)
      .transitionTo("withdraw")
      .thenReply("transfer started");
  }
}
