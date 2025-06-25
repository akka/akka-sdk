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

  public record Withdraw(String from, int amount) {
  }

  // end::class[]

  // tag::definition[]
  public record Deposit(String to, int amount) {
  }

  final private ComponentClient componentClient;

  public TransferWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowDef<TransferState> definition() {
    return workflow() // <1>
      .addStep(withdrawStep())
      .addStep(depositStep());
  }

  private Step withdrawStep() {
    return
        step("withdraw") // <2>
            .call(Withdraw.class, cmd ->
                componentClient.forEventSourcedEntity(cmd.from) // <3>
                    .method(WalletEntity::withdraw)
                    .invoke(cmd.amount)) // <4>
            .andThen(() -> {
              Deposit depositInput = new Deposit(currentState().transfer().to(), currentState().transfer().amount());
              return effects()
                  .updateState(currentState().withStatus(WITHDRAW_SUCCEEDED))
                  .transitionTo("deposit", depositInput); // <5>
            });
  }

  private Step depositStep() {
    return
        step("deposit") // <6>
            .call(Deposit.class, cmd ->
                componentClient.forEventSourcedEntity(cmd.to)
                    .method(WalletEntity::deposit)
                    .invoke(cmd.amount))
            .andThen(() -> {
              return effects()
                  .updateState(currentState().withStatus(COMPLETED))
                  .end(); // <7>
            });
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
        .transitionTo("withdraw", withdrawInput) // <7>
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
