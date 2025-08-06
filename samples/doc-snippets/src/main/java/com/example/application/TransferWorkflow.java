package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;
import com.example.domain.TransferState;
import com.example.domain.TransferState.Transfer;

import static akka.Done.done;
import static com.example.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.domain.TransferState.TransferStatus.WITHDRAW_SUCCEEDED;
import static java.time.Duration.ofSeconds;

// tag::class[]
@ComponentId("transfer") // <1>
public class TransferWorkflow extends Workflow<TransferState> { // <2>
  public record Withdraw(String from, int amount) {
  }

  @Override
  public WorkflowConfig configuration() { // <3>
    return WorkflowConfig.builder()
      .defaultStepTimeout(ofSeconds(2))
      .build();
  }

  private StepEffect withdrawStep() { // <4>
    // FIXME implement this

    return stepEffects() // <5>
      .updateState(currentState().withStatus(WITHDRAW_SUCCEEDED))
      .thenTransitionTo(TransferWorkflow::depositStep);
  }

  private StepEffect depositStep() {
    // FIXME implement this

    return stepEffects()
      .updateState(currentState().withStatus(COMPLETED))
      .thenEnd();
  }

  public Effect<Done> startTransfer(Transfer transfer) { // <6>
    TransferState initialState = new TransferState(transfer);

    return effects()// <7>
      .updateState(initialState)
      .transitionTo(TransferWorkflow::withdrawStep)
      .thenReply(done());
  }

}
// end::class[]
