package com.example.transfer.application;

import akka.Done;
import com.example.wallet.application.WalletEntity;
import com.example.transfer.domain.TransferState;
import com.example.transfer.domain.TransferState.Transfer;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;

import static akka.Done.done;
import static com.example.transfer.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.WITHDRAW_SUCCEED;

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

  // end::definition[]

  final private ComponentClient componentClient;

  public TransferWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::definition[]
  @Override
  public WorkflowDef<TransferState> definition() {
    Step withdraw =
      step("withdraw") // <1>
        .asyncCall(Withdraw.class, cmd ->
          componentClient.forKeyValueEntity(cmd.from)
            .method(WalletEntity::withdraw)
            .invokeAsync(cmd.amount)) // <2>
        .andThen(Done.class, __ -> {
          Deposit depositInput = new Deposit(currentState().transfer().to(), currentState().transfer().amount());
          return effects()
            .updateState(currentState().withStatus(WITHDRAW_SUCCEED))
            .transitionTo("deposit", depositInput); // <3>
        });

    Step deposit =
      step("deposit") // <1>
        .asyncCall(Deposit.class, cmd ->
          componentClient.forKeyValueEntity(cmd.to)
            .method(WalletEntity::deposit)
            .invokeAsync(cmd.amount)) // <4>
        .andThen(Done.class, __ -> {
          return effects()
            .updateState(currentState().withStatus(COMPLETED))
            .end(); // <5>
        });

    return workflow() // <6>
      .addStep(withdraw)
      .addStep(deposit);
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
}
