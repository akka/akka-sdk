package com.example.transfer.application;

import static akka.Done.done;
import static com.example.transfer.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.WITHDRAW_SUCCEEDED;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.transfer.domain.TransferState;
import com.example.transfer.domain.TransferState.Transfer;
import com.example.wallet.application.WalletEntity;

// tag::class[]
@Component(id = "transfer") // <1>
public class TransferWorkflow extends Workflow<TransferState> { // <2>

  // end::class[]

  // tag::definition[]
  public record Withdraw(String from, int amount) {} // <1>

  public record Deposit(String to, int amount) {} // <1>

  private final ComponentClient componentClient;

  public TransferWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @StepName("withdraw") // <2>
  private StepEffect withdrawStep(Withdraw withdraw) {
    componentClient
      .forEventSourcedEntity(withdraw.from)
      .method(WalletEntity::withdraw)
      .invoke(withdraw.amount); // <3>

    String to = currentState().transfer().to(); // <4>
    int amount = currentState().transfer().amount();
    Deposit depositInput = new Deposit(to, amount);

    return stepEffects()
      .updateState(currentState().withStatus(WITHDRAW_SUCCEEDED))
      .thenTransitionTo(TransferWorkflow::depositStep) // <5>
      .withInput(depositInput);
  }

  @StepName("deposit") // <2>
  private StepEffect depositStep(Deposit deposit) { // <6>
    componentClient
      .forEventSourcedEntity(deposit.to)
      .method(WalletEntity::deposit)
      .invoke(deposit.amount);

    // prettier-ignore
    return stepEffects()
      .updateState(currentState().withStatus(COMPLETED))
      .thenEnd(); // <7>
  }

  // end::definition[]

  // tag::class[]
  public Effect<Done> startTransfer(Transfer transfer) { // <3>
    if (transfer.amount() <= 0) { // <4>
      return effects().error("transfer amount should be greater than zero");
    } else if (currentState() != null) {
      return effects().error("transfer already started");
    } else {
      TransferState initialState = new TransferState(transfer); // <5>

      Withdraw withdrawInput = new Withdraw(transfer.from(), transfer.amount());

      return effects()
        .updateState(initialState) // <6>
        .transitionTo(TransferWorkflow::withdrawStep) // <7>
        .withInput(withdrawInput)
        .thenReply(done()); // <8>
    }
  }

  // end::class[]

  // tag::get-transfer[]
  public ReadOnlyEffect<TransferState> getTransferState() {
    if (currentState() == null) {
      return effects().error("transfer not started");
    } else {
      return effects().reply(currentState()); // <1>
    }
  }

  // end::get-transfer[]

  // tag::delete-workflow[]
  public Effect<Done> delete() {
    return effects()
      .delete() // <1>
      .thenReply(done());
  }
  // end::delete-workflow[]
}
