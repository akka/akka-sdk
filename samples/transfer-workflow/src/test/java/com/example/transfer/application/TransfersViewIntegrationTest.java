package com.example.transfer.application;

import akka.javasdk.testkit.EventingTestKit;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.transfer.application.TransfersView.TransferEntries;
import com.example.transfer.application.TransfersView.TransferEntry;
import com.example.transfer.domain.TransferState;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class TransfersViewIntegrationTest extends TestKitSupport {

  private EventingTestKit.IncomingMessages transferStates;

  public TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      .withWorkflowIncomingMessages("transfer");

  }

  @BeforeAll
  public void beforeAll() {
    super.beforeAll();
    transferStates = testKit.getWorkflowIncomingMessages("transfer");
  }

  @Test
  public void shouldTestTransferViewWithPredefinedStateChanges() {
    //given
    var messageBuilder = testKit.getMessageBuilder();

    var state1 = new TransferState(new TransferState.Transfer("from", "to", 100), TransferState.TransferStatus.STARTED);
    var state2 = new TransferState(new TransferState.Transfer("from", "to", 200), TransferState.TransferStatus.COMPLETED);

    //when
    transferStates.publish(messageBuilder.of(state1, "t1"));
    transferStates.publish(messageBuilder.of(state2, "t2"));

    //then
    Awaitility.await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {

        TransferEntries result = componentClient.forView().method(TransfersView::getAllCompleted).invoke();
        assertThat(result.entries()).contains(new TransferEntry("t2", "COMPLETED"));
      });
  }

}