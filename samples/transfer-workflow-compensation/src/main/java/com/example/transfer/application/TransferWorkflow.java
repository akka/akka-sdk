package com.example.transfer.application;

import static com.example.transfer.domain.TransferState.TransferStatus.COMPENSATION_COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.DEPOSIT_FAILED;
import static com.example.transfer.domain.TransferState.TransferStatus.REQUIRES_MANUAL_INTERVENTION;
import static com.example.transfer.domain.TransferState.TransferStatus.TRANSFER_ACCEPTANCE_TIMED_OUT;
import static com.example.transfer.domain.TransferState.TransferStatus.WAITING_FOR_ACCEPTANCE;
import static com.example.transfer.domain.TransferState.TransferStatus.WITHDRAW_FAILED;
import static com.example.transfer.domain.TransferState.TransferStatus.WITHDRAW_SUCCEEDED;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofSeconds;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import com.example.transfer.application.FraudDetectionService.FraudDetectionResult;
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

@ComponentId("transfer") // <1>
public class TransferWorkflow extends Workflow<TransferState> {

  private static final Logger logger = LoggerFactory.getLogger(TransferWorkflow.class);

  private final ComponentClient componentClient;
  private final FraudDetectionService fraudDetectionService;
  private final String workflowId;

  public TransferWorkflow(
    ComponentClient componentClient,
    FraudDetectionService fraudDetectionService,
    WorkflowContext workflowContext
  ) {
    this.componentClient = componentClient;
    this.fraudDetectionService = fraudDetectionService;
    this.workflowId = workflowContext.workflowId();
  }


  // tag::timeouts[]
  // tag::recover-strategy[]
  // tag::step-timeout[]
  @Override
  public WorkflowConfig configuration() {
    return WorkflowConfig.builder()
      // end::recover-strategy[]
      // end::step-timeout[]
      .workflowTimeout(ofSeconds(5)) // <1>
      .defaultStepTimeout(ofSeconds(2)) // <2>
      // end::timeouts[]
      // tag::step-timeout[]
      .stepConfig(TransferWorkflow::failoverHandlerStep, ofSeconds(1)) // <1>
      // end::step-timeout[]
      // tag::recover-strategy[]
      .workflowRecovery(maxRetries(0).failoverTo(TransferWorkflow::failoverHandlerStep)) // <1>
      .defaultStepRecovery(maxRetries(1).failoverTo(TransferWorkflow::failoverHandlerStep)) // <2>
      .stepConfig(TransferWorkflow::depositStep, maxRetries(2).failoverTo(TransferWorkflow::compensateWithdrawStep)) // <3>
      // tag::step-timeout[]
      // tag::timeouts[]
      .build();
  }
  // end::timeouts[]
  // end::step-timeout[]
  // end::recover-strategy[]


  // tag::detect-frauds[]
  private Step detectFraudsStep() {
    return step("detect-frauds")
      .call(() -> { // <1>
        // end::detect-frauds[]
        logger.info("Transfer {} - detecting frauds", currentState().transferId());
        // tag::detect-frauds[]
        return fraudDetectionService.detectFrauds(currentState().transfer());
      })
      .andThen(FraudDetectionResult.class, result -> {
        var transfer = currentState().transfer();
        return switch (result) {
          case ACCEPTED -> { // <2>
            // end::detect-frauds[]
            logger.info("Running: " + transfer);
            // tag::detect-frauds[]
            TransferState initialState = TransferState.create(workflowId, transfer);
            Withdraw withdrawInput = new Withdraw(
              initialState.withdrawId(),
              transfer.amount()
            );
            yield effects().updateState(initialState).transitionTo("withdraw", withdrawInput);
          }
          case MANUAL_ACCEPTANCE_REQUIRED -> { // <3>
            // end::detect-frauds[]
            logger.info("Waiting for acceptance: " + transfer);
            // tag::detect-frauds[]
            TransferState waitingForAcceptanceState = TransferState.create(
              workflowId,
              transfer
            ).withStatus(WAITING_FOR_ACCEPTANCE);
            yield effects()
              .updateState(waitingForAcceptanceState)
              .transitionTo("wait-for-acceptance");
          }
        };
      });
  }

  // end::detect-frauds[]

  private StepEffect withdrawStep(Withdraw withdraw) {

    logger.info("Running withdraw: {}", withdraw);

    // cancelling the timer in case it was scheduled
    timers().delete("acceptanceTimout-" + currentState().transferId());

    WalletResult result = componentClient.forEventSourcedEntity(currentState().transfer().from())
      .method(WalletEntity::withdraw)
      .invoke(withdraw);

    return switch (result) {
      case Success __ -> {
        Deposit depositInput = new Deposit(currentState().depositId(), currentState().transfer().amount());
        yield stepEffects()
          .updateState(currentState().withStatus(WITHDRAW_SUCCEEDED))
          .thenTransitionTo(TransferWorkflow::depositStep)
          .withInput(depositInput);
      }
      case Failure failure -> {
        logger.warn("Withdraw failed with msg: {}", failure.errorMsg());
        yield stepEffects()
          .updateState(currentState().withStatus(WITHDRAW_FAILED))
          .thenEnd();
      }
    };
  }

  // tag::compensation[]
  private StepEffect depositStep(Deposit deposit) {
    // end::compensation[]
    logger.info("Running deposit: {}", deposit);
    // tag::compensation[]
    String to = currentState().transfer().to();
    WalletResult result = componentClient
          .forEventSourcedEntity(to)
      .method(WalletEntity::deposit) // <1>
      .invoke(deposit);

    return switch (result) {
      case Success __ -> // <2>
        stepEffects()
          .updateState(currentState().withStatus(COMPLETED))
          .thenEnd();
      case Failure failure -> {
        // end::compensation[]
        logger.warn("Deposit failed with msg: {}", failure.errorMsg());
        // tag::compensation[]
        yield stepEffects()
          .updateState(currentState().withStatus(DEPOSIT_FAILED))
          .thenTransitionTo(TransferWorkflow::compensateWithdrawStep); // <3>
      }
    };
  }

  private StepEffect compensateWithdrawStep() { // <4>
    // end::compensation[]
    logger.info("Running withdraw compensation");
    // tag::compensation[]
    var transfer = currentState().transfer();
    // end::compensation[]
    // depositId is reused for the compensation, just to have a stable commandId and simplify the example
    // tag::compensation[]
    String commandId = currentState().depositId();
    WalletResult result = componentClient.forEventSourcedEntity(transfer.from())
      .method(WalletEntity::deposit)
      .invoke(new Deposit(commandId, transfer.amount()));

    return switch (result) {
      case Success __ -> // <5>
        stepEffects()
          .updateState(currentState().withStatus(COMPENSATION_COMPLETED))
          .thenEnd();
      case Failure __ -> // <6>
        throw new IllegalStateException("Expecting succeed operation but received: " + result);
    };

  }

  // end::compensation[]

  // tag::pausing[]
  private StepEffect waitForAcceptationStep() {
    String transferId = currentState().transferId();
    timers().createSingleTimer(
      "acceptationTimeout-" + transferId,
      ofHours(8),
      componentClient.forWorkflow(transferId)
        .method(TransferWorkflow::acceptationTimeout)
        .deferred()); // <1>

    return stepEffects()
      .thenPause(); // <2>
  }

  // end::pausing[]


  private StepEffect failoverHandlerStep() {
    logger.info("Running workflow failed step");

    return stepEffects()
      .updateState(currentState().withStatus(REQUIRES_MANUAL_INTERVENTION))
      .thenEnd();
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
          .transitionTo(TransferWorkflow::waitForAcceptationStep)
          .thenReply("transfer started, waiting for acceptation");
      } else {
        logger.info("Running: " + transfer);
        TransferState initialState = TransferState.create(workflowId, transfer);
        Withdraw withdrawInput = new Withdraw(initialState.withdrawId(), transfer.amount());
        return effects()
          .updateState(initialState)
          .transitionTo(TransferWorkflow::withdrawStep)
          .withInput(withdrawInput)
          .thenReply("transfer started");
      }
    }
  }

  public Effect<String> acceptanceTimeout() {
    if (currentState() == null) {
      return effects().error("transfer not started");
    } else if (currentState().status() == WAITING_FOR_ACCEPTANCE) {
      return effects()
        .updateState(currentState().withStatus(TRANSFER_ACCEPTANCE_TIMED_OUT))
        .end()
        .thenReply("timed out");
    } else {
      logger.info("Ignoring acceptance timeout for status: " + currentState().status());
      return effects().reply("Ok");
    }
  }

  // tag::resuming[]
  public Effect<String> accept() {
    if (currentState() == null) {
      return effects().error("transfer not started");
    } else if (currentState().status() == WAITING_FOR_ACCEPTANCE) { // <1>
      Transfer transfer = currentState().transfer();
      // end::resuming[]
      logger.info("Accepting transfer: " + transfer);
      // tag::resuming[]
      Withdraw withdrawInput = new Withdraw(currentState().withdrawId(), transfer.amount());
      return effects().transitionTo("withdraw", withdrawInput).thenReply("transfer accepted");
    } else { // <2>
      return effects()
        .transitionTo(TransferWorkflow::withdrawStep)
        .withInput(withdrawInput)
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
