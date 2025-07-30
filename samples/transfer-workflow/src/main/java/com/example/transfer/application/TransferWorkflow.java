package com.example.transfer.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.transfer.domain.TransferState;
import com.example.transfer.domain.TransferState.Transfer;
import com.example.wallet.application.WalletEntity;

import static akka.Done.done;
import static com.example.transfer.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.WITHDRAW_SUCCEEDED;

// tag::class[]
@ComponentId("transfer") // <1>
public class TransferWorkflow extends Workflow<TransferState> { // <2>
  // end::class[]

  // tag::class[]

  // tag::definition[]
  public record Withdraw(String from, int amount) { // <1>
  }
  // end::class[]
  public record Deposit(String to, int amount) { // <1>
  }

  final private ComponentClient componentClient;

  public TransferWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  private StepEffect withdrawStep(Withdraw withdraw) {

    componentClient.forEventSourcedEntity(withdraw.from)
      .method(WalletEntity::withdraw)
      .invoke(withdraw.amount); // <2>

    String to = currentState().transfer().to(); // <3>
    int amount = currentState().transfer().amount();
    Deposit depositInput = new Deposit(to, amount);

    return stepEffects()
      .updateState(currentState().withStatus(WITHDRAW_SUCCEEDED))
      .thenTransitionTo(TransferWorkflow::depositStep) // <4>
      .withInput(depositInput);
  }

  private StepEffect depositStep(Deposit deposit) { // <5>

    componentClient.forEventSourcedEntity(deposit.to)
      .method(WalletEntity::deposit)
      .invoke(deposit.amount);

    return stepEffects()
      .updateState(currentState().withStatus(COMPLETED))
      .thenEnd(); // <6>
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
