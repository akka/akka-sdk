package com.example.transfer.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.transfer.domain.TransferState;
import com.example.transfer.domain.TransferState.Transfer;
import com.example.wallet.application.WalletEntity;
import com.example.wallet.application.WalletEntity.WalletResult;
import com.example.wallet.application.WalletEntity.WalletResult.Failure;
import com.example.wallet.application.WalletEntity.WalletResult.Success;
import com.example.wallet.domain.WalletCommand.Deposit;
import com.example.wallet.domain.WalletCommand.Withdraw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.transfer.domain.TransferState.TransferStatus.COMPENSATION_COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.DEPOSIT_FAILED;
import static com.example.transfer.domain.TransferState.TransferStatus.REQUIRES_MANUAL_INTERVENTION;
import static com.example.transfer.domain.TransferState.TransferStatus.TRANSFER_ACCEPTATION_TIMED_OUT;
import static com.example.transfer.domain.TransferState.TransferStatus.WAITING_FOR_ACCEPTATION;
import static com.example.transfer.domain.TransferState.TransferStatus.WITHDRAW_FAILED;
import static com.example.transfer.domain.TransferState.TransferStatus.WITHDRAW_SUCCEED;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofSeconds;

@ComponentId("transfer") // <1>
public class TransferWorkflow extends Workflow<TransferState> {

  private static final Logger logger = LoggerFactory.getLogger(TransferWorkflow.class);

  final private ComponentClient componentClient;

  public TransferWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::definition[]
  @Override
  public WorkflowDef<TransferState> definition() {
    Step withdraw =
      step("withdraw")
        .call(Withdraw.class, cmd -> {
          logger.info("Running withdraw: {}", cmd);

          // cancelling the timer in case it was scheduled
          timers().cancel("acceptationTimout-" + currentState().transferId());

          return componentClient.forEventSourcedEntity(currentState().transfer().from())
              .method(WalletEntity::withdraw)
              .invoke(cmd);
        })
        .andThen(WalletResult.class, result -> {
          switch (result) {
            case Success __ -> {
              Deposit depositInput = new Deposit(currentState().depositId(), currentState().transfer().amount());
              return effects()
                .updateState(currentState().withStatus(WITHDRAW_SUCCEED))
                .transitionTo("deposit", depositInput);
            }
            case Failure failure -> {
              logger.warn("Withdraw failed with msg: {}", failure.errorMsg());
              return effects()
                .updateState(currentState().withStatus(WITHDRAW_FAILED))
                .end();

            }
          }
        });

    // tag::compensation[]
    Step deposit =
      step("deposit")
        .call(Deposit.class, cmd -> {
          // end::compensation[]
          logger.info("Running deposit: {}", cmd);
          // tag::compensation[]
          return componentClient.forEventSourcedEntity(currentState().transfer().to())
            .method(WalletEntity::deposit)
            .invoke(cmd);
        })
        .andThen(WalletResult.class, result -> { // <1>
          switch (result) {
            case Success __ -> {
              return effects()
                .updateState(currentState().withStatus(COMPLETED))
                .end(); // <2>
            }
            case Failure failure -> {
              // end::compensation[]
              logger.warn("Deposit failed with msg: {}", failure.errorMsg());
              // tag::compensation[]
              return effects()
                .updateState(currentState().withStatus(DEPOSIT_FAILED))
                .transitionTo("compensate-withdraw"); // <3>
            }
          }
        });

    Step compensateWithdraw =
      step("compensate-withdraw") // <4>
        .call(() -> {
          // end::compensation[]
          logger.info("Running withdraw compensation");
          // tag::compensation[]
          var transfer = currentState().transfer();
          // end::compensation[]
          // depositId is reused for the compensation, just to have a stable commandId and simplify the example
          // tag::compensation[]
          String commandId = currentState().depositId();
          return componentClient.forEventSourcedEntity(transfer.from())
            .method(WalletEntity::deposit)
            .invoke(new Deposit(commandId, transfer.amount()));
        })
        .andThen(WalletResult.class, result -> {
          switch (result) {
            case Success __ -> {
              return effects()
                .updateState(currentState().withStatus(COMPENSATION_COMPLETED))
                .end(); // <5>
            }
            case Failure __ -> { // <6>
              throw new IllegalStateException("Expecting succeed operation but received: " + result);
            }
          }
        });
    // end::compensation[]

    // tag::step-timeout[]
    Step failoverHandler =
      step("failover-handler")
        .call(() -> {
          // end::step-timeout[]
          logger.info("Running workflow failed step");
          // tag::step-timeout[]
          return "handling failure";
        })
        .andThen(String.class, __ -> effects()
          .updateState(currentState().withStatus(REQUIRES_MANUAL_INTERVENTION))
          .end())
        .timeout(ofSeconds(1)); // <1>
    // end::step-timeout[]

    // tag::pausing[]
    Step waitForAcceptation =
      step("wait-for-acceptation")
        .call(() -> {
          String transferId = currentState().transferId();
          timers().createSingleTimer(
            "acceptationTimeout-" + transferId,
            ofHours(8),
            componentClient.forWorkflow(transferId)
              .method(TransferWorkflow::acceptationTimeout)
              .deferred()); // <1>
          return Done.done();
        })
        .andThen(Done.class, __ ->
          effects().pause()); // <2>
    // end::pausing[]

    // tag::timeouts[]
    // tag::recover-strategy[]
    return workflow()
      // end::recover-strategy[]
      // end::timeouts[]
      // tag::timeouts[]
      .timeout(ofSeconds(5)) // <1>
      .defaultStepTimeout(ofSeconds(2)) // <2>
      // end::timeouts[]
      // tag::recover-strategy[]
      .failoverTo("failover-handler", maxRetries(0)) // <1>
      .defaultStepRecoverStrategy(maxRetries(1).failoverTo("failover-handler")) // <2>
      .addStep(withdraw)
      .addStep(deposit, maxRetries(2).failoverTo("compensate-withdraw")) // <3>
      // end::recover-strategy[]
      .addStep(compensateWithdraw)
      .addStep(waitForAcceptation)
      .addStep(failoverHandler);
  }

  public Effect<String> startTransfer(Transfer transfer) {
    if (currentState() != null) {
      return effects().error("transfer already started");
    } else if (transfer.amount() <= 0) {
      return effects().error("transfer amount should be greater than zero");
    } else {
      String workflowId = commandContext().workflowId();
      if (transfer.amount() > 1000) {
        logger.info("Waiting for acceptation: " + transfer);
        TransferState waitingForAcceptationState = TransferState.create(workflowId, transfer)
          .withStatus(WAITING_FOR_ACCEPTATION);
        return effects()
          .updateState(waitingForAcceptationState)
          .transitionTo("wait-for-acceptation")
          .thenReply("transfer started, waiting for acceptation");
      } else {
        logger.info("Running: " + transfer);
        TransferState initialState = TransferState.create(workflowId, transfer);
        Withdraw withdrawInput = new Withdraw(initialState.withdrawId(), transfer.amount());
        return effects()
          .updateState(initialState)
          .transitionTo("withdraw", withdrawInput)
          .thenReply("transfer started");
      }
    }
  }

  public Effect<String> acceptationTimeout() {
    if (currentState() == null) {
      return effects().error("transfer not started");
    } else if (currentState().status() == WAITING_FOR_ACCEPTATION) {
      return effects()
        .updateState(currentState().withStatus(TRANSFER_ACCEPTATION_TIMED_OUT))
        .end()
        .thenReply("timed out");
    } else {
      logger.info("Ignoring acceptation timeout for status: " + currentState().status());
      return effects().reply("Ok");
    }
  }

  // tag::resuming[]
  public Effect<String> accept() {
    if (currentState() == null) {
      return effects().error("transfer not started");
    } else if (currentState().status() == WAITING_FOR_ACCEPTATION) { // <1>
      Transfer transfer = currentState().transfer();
      // end::resuming[]
      logger.info("Accepting transfer: " + transfer);
      // tag::resuming[]
      Withdraw withdrawInput = new Withdraw(currentState().withdrawId(), transfer.amount());
      return effects()
        .transitionTo("withdraw", withdrawInput)
        .thenReply("transfer accepted");
    } else { // <2>
      return effects().error("Cannot accept transfer with status: " + currentState().status());
    }
  }
  // end::resuming[]

  public Effect<TransferState> getTransferState() {
    if (currentState() == null) {
      return effects().error("transfer not started");
    } else {
      return effects().reply(currentState());
    }
  }
}
