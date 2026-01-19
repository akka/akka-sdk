package com.example.application;

import akka.Done;
import akka.javasdk.NotificationPublisher;
import akka.javasdk.NotificationPublisher.NotificationStream;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import com.example.domain.TransferState;
import com.example.domain.TransferState.Transfer;

import static akka.Done.done;
import static com.example.domain.TransferState.TransferStatus.COMPLETED;
import static com.example.domain.TransferState.TransferStatus.WITHDRAW_SUCCEEDED;
import static java.time.Duration.ofSeconds;

// tag::workflow-notification[]
@Component(id = "transfer")
public class TransferWorkflowWithNotifications extends Workflow<TransferState> {

  private final NotificationPublisher<String> notificationPublisher;

  public TransferWorkflowWithNotifications(NotificationPublisher<String> notificationPublisher) { // <1>
    this.notificationPublisher = notificationPublisher;
  }

  private StepEffect withdrawStep() {
    // TODO: implement your step logic here
    // prettier-ignore
    notificationPublisher.publish("Withdraw completed"); // <2>
    return stepEffects()
      .updateState(currentState().withStatus(WITHDRAW_SUCCEEDED))
      .thenTransitionTo(TransferWorkflowWithNotifications::depositStep);
  }

  private StepEffect depositStep() {
    // TODO: implement your step logic here
    // prettier-ignore
    notificationPublisher.publish("Deposit completed"); // <2>
    return stepEffects().updateState(currentState().withStatus(COMPLETED)).thenEnd();
  }

  public NotificationStream<String> updates() { // <3>
    return notificationPublisher.stream();
  }
  // end::workflow-notification[]

  public Effect<Done> startTransfer(Transfer transfer) {
    TransferState initialState = new TransferState(transfer);

    return effects() // <7>
      .updateState(initialState)
      .transitionTo(TransferWorkflowWithNotifications::withdrawStep)
      .thenReply(done());
  }
  // tag::workflow-notification[]
}
// end::workflow-notification[]
