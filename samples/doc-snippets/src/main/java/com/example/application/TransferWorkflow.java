package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;
import com.example.domain.TransferState;
import com.example.domain.TransferState.Transfer;

import static akka.Done.done;
import static com.example.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.domain.TransferState.TransferStatus.WITHDRAW_SUCCEEDED;

// tag::class[]
@ComponentId("transfer") // <1>
public class TransferWorkflow extends Workflow<TransferState> { // <2>
  public record Withdraw(String from, int amount) {
  }

  @Override
  public WorkflowDef<TransferState> definition() { // <3>
    return workflow()
        .addStep(withdrawStep())
        .addStep(depositStep());
  }

  private Step withdrawStep() {
    return
        step("withdraw") // <4>
            .call(() -> {// <5>
              return null; // FIXME implement this
            })
            .andThen(() -> { // <6>
              return effects()
                  .updateState(currentState().withStatus(WITHDRAW_SUCCEEDED))
                  .transitionTo("deposit");
            });
  }

  private Step depositStep() {
    return
        step("deposit")
            .call(() -> {
              return null; // FIXME implement this
            })
            .andThen(() -> {
              return effects()
                  .updateState(currentState().withStatus(COMPLETED))
                  .end();
            });
  }

  public Effect<Done> startTransfer(Transfer transfer) { // <7>
    TransferState initialState = new TransferState(transfer);

    return effects()// <8>
        .updateState(initialState)
        .transitionTo("withdraw")
        .thenReply(done());
  }

}
// end::class[]
