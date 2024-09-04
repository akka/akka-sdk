package com.example.transfer.application;

import akka.Done;
import com.example.wallet.application.WalletEntity;
import com.example.transfer.domain.Message;
import com.example.transfer.domain.TransferState;
import com.example.transfer.domain.TransferState.Transfer;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.wallet.domain.WalletCmd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.transfer.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.WITHDRAW_SUCCEED;

// tag::class[]
@ComponentId("transfer")
public class TransferWorkflow extends Workflow<TransferState> { // <1>
  // end::class[]

  // tag::start[]
  public record Withdraw(String from, int amount) { // <4>
  }

  // end::start[]

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
          componentClient.forEventSourcedEntity(cmd.from)
            .method(WalletEntity::withdraw)
            .invokeAsync(new WalletCmd.WithdrawCmd(cmd.amount))) // <2>
        .andThen(Done.class, __ -> {
          Deposit depositInput = new Deposit(currentState().transfer().to(), currentState().transfer().amount());
          return effects()
            .updateState(currentState().withStatus(WITHDRAW_SUCCEED))
            .transitionTo("deposit", depositInput); // <3>
        });

    Step deposit =
      step("deposit") // <1>
        .asyncCall(Deposit.class, cmd ->
          componentClient.forEventSourcedEntity(cmd.to)
            .method(WalletEntity::deposit)
            .invokeAsync(new WalletCmd.DepositCmd(cmd.amount))) // <4>
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

  // tag::start[]
  public Effect<Message> startTransfer(Transfer transfer) {
    if (transfer.amount() <= 0) {
      return effects().error("transfer amount should be greater than zero"); // <1>
    } else if (currentState() != null) {
      return effects().error("transfer already started"); // <2>
    } else {

      TransferState initialState = new TransferState(transfer); // <3>

      Withdraw withdrawInput = new Withdraw(transfer.from(), transfer.amount());

      return effects()
        .updateState(initialState) // <4>
        .transitionTo("withdraw", withdrawInput) // <5>
        .thenReply(new Message("transfer started")); // <6>
    }
  }
  // end::start[]

  // tag::get-transfer[]
  public Effect<TransferState> getTransferState() {
    if (currentState() == null) {
      return effects().error("transfer not started");
    } else {
      return effects().reply(currentState()); // <2>
    }
  }
  // end::get-transfer[]
}
