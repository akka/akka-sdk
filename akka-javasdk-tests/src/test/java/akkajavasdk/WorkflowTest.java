/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.actions.echo.Message;
import akkajavasdk.components.views.TransferView;
import akkajavasdk.components.workflowentities.DummyTransferStore;
import akkajavasdk.components.workflowentities.DummyWorkflow;
import akkajavasdk.components.workflowentities.FailingCounterEntity;
import akkajavasdk.components.workflowentities.Transfer;
import akkajavasdk.components.workflowentities.TransferWorkflow;
import akkajavasdk.components.workflowentities.TransferWorkflowWithFraudDetection;
import akkajavasdk.components.workflowentities.TransferWorkflowWithoutInputs;
import akkajavasdk.components.workflowentities.WalletEntity;
import akkajavasdk.components.workflowentities.WorkflowWithDefaultRecoverStrategy;
import akkajavasdk.components.workflowentities.WorkflowWithRecoverStrategy;
import akkajavasdk.components.workflowentities.WorkflowWithRecoverStrategyAndAsyncCall;
import akkajavasdk.components.workflowentities.WorkflowWithStepTimeout;
import akkajavasdk.components.workflowentities.WorkflowWithTimeout;
import akkajavasdk.components.workflowentities.WorkflowWithTimer;
import akkajavasdk.components.workflowentities.WorkflowWithoutInitialState;
import akkajavasdk.components.workflowentities.hierarchy.TextWorkflow;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static akkajavasdk.components.workflowentities.TransferConsumer.TRANSFER_CONSUMER_STORE;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(Junit5LogCapturing.class)
public class WorkflowTest extends TestKitSupport {

  private static final Logger log = LoggerFactory.getLogger(WorkflowTest.class);

  @Test
  public void shouldNotStartTransferForWithNegativeAmount() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, -10);

    Message message =
      await(
        componentClient.forWorkflow(transferId)
          .method(TransferWorkflow::startTransfer)
          .invokeAsync(transfer));

    assertThat(message.text()).isEqualTo("Transfer amount should be greater than zero");
  }

  @Test
  public void shouldTransferMoney() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);


    Message response =
      await(componentClient.forWorkflow(transferId)
        .method(TransferWorkflow::startTransfer)
        .invokeAsync(transfer));

    assertThat(response.text()).contains("transfer started");


    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var balance1 = getWalletBalance(walletId1);
        var balance2 = getWalletBalance(walletId2);

        assertThat(balance1).isEqualTo(90);
        assertThat(balance2).isEqualTo(110);
      });
  }

  @Test
  public void shouldVerifyWorkflowSubscriptions() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId1 = randomTransferId();
    var transferId2 = randomTransferId();
    var transfer1 = new Transfer(walletId1, walletId2, 10);
    var transfer2 = new Transfer(walletId1, walletId2, 20);

    await(componentClient.forWorkflow(transferId1)
      .method(TransferWorkflow::startTransfer)
      .invokeAsync(transfer1));
    await(componentClient.forWorkflow(transferId2)
      .method(TransferWorkflow::startTransfer)
      .invokeAsync(transfer2));

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var transferState1 = DummyTransferStore.get(TRANSFER_CONSUMER_STORE, transferId1);
        var transferState2 = DummyTransferStore.get(TRANSFER_CONSUMER_STORE, transferId2);

        assertThat(transferState1.transfer()).isEqualTo(transfer1);
        assertThat(transferState2.transfer()).isEqualTo(transfer2);

        var result = await(componentClient.forView().method(TransferView::getAll).invokeAsync());
        assertThat(result.entries()).contains(
          new TransferView.TransferEntry(transferId1, true),
          new TransferView.TransferEntry(transferId2, true));
      });
  }


  @Test
  public void shouldTransferMoneyWithoutStepInputs() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    Message response = await(
      componentClient.forWorkflow(transferId)
        .method(TransferWorkflowWithoutInputs::startTransfer)
        .invokeAsync(transfer));

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var balance1 = getWalletBalance(walletId1);
        var balance2 = getWalletBalance(walletId2);

        assertThat(balance1).isEqualTo(90);
        assertThat(balance2).isEqualTo(110);
      });
  }

  @Test
  public void shouldTransferAsyncMoneyWithoutStepInputs() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);


    Message response = await(
      componentClient.forWorkflow(transferId)
        .method(TransferWorkflowWithoutInputs::startTransferAsync)
        .invokeAsync(transfer));

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var balance1 = getWalletBalance(walletId1);
        var balance2 = getWalletBalance(walletId2);

        assertThat(balance1).isEqualTo(90);
        assertThat(balance2).isEqualTo(110);
      });
  }

  @Test
  public void shouldTransferMoneyWithFraudDetection() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);


    Message response = await(
      componentClient.forWorkflow(transferId)
        .method(TransferWorkflowWithFraudDetection::startTransfer)
        .invokeAsync(transfer));

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var balance1 = getWalletBalance(walletId1);
        var balance2 = getWalletBalance(walletId2);

        assertThat(balance1).isEqualTo(90);
        assertThat(balance2).isEqualTo(110);
      });
  }

  @Test
  public void shouldTransferMoneyWithFraudDetectionAndManualAcceptance() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100000);
    createWallet(walletId2, 100000);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 1000);

    Message response = await(
      componentClient.forWorkflow(transferId)
        .method(TransferWorkflowWithFraudDetection::startTransfer)
        .invokeAsync(transfer));

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {

        var transferState = await(
          componentClient.forWorkflow(transferId)
            .method(TransferWorkflowWithFraudDetection::getTransferState)
            .invokeAsync());

        assertThat(transferState.finished()).isFalse();
        assertThat(transferState.accepted()).isFalse();
        assertThat(transferState.lastStep()).isEqualTo("fraud-detection");
      });


    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {

        Message acceptedResponse = await(
          componentClient.forWorkflow(transferId)
            .method(TransferWorkflowWithFraudDetection::acceptTransfer)
            .invokeAsync());

        assertThat(acceptedResponse.text()).isEqualTo("transfer accepted");
      });


    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var balance1 = getWalletBalance(walletId1);
        var balance2 = getWalletBalance(walletId2);

        assertThat(balance1).isEqualTo(99000);
        assertThat(balance2).isEqualTo(101000);
      });
  }

  @Test
  public void shouldNotTransferMoneyWhenFraudDetectionRejectTransfer() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 1000000);


    Message response = await(
      componentClient.forWorkflow(transferId)
        .method(TransferWorkflowWithFraudDetection::startTransfer)
        .invokeAsync(transfer));

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var balance1 = getWalletBalance(walletId1);
        var balance2 = getWalletBalance(walletId2);

        assertThat(balance1).isEqualTo(100);
        assertThat(balance2).isEqualTo(100);

        var transferState = await(
          componentClient.forWorkflow(transferId)
            .method(TransferWorkflowWithFraudDetection::getTransferState)
            .invokeAsync());

        assertThat(transferState.finished()).isTrue();
        assertThat(transferState.accepted()).isFalse();
        assertThat(transferState.lastStep()).isEqualTo("fraud-detection");
      });
  }

  @Test
  public void shouldRecoverFailingCounterWorkflowWithDefaultRecoverStrategy() {
    //given
    var counterId = randomId();
    var workflowId = randomId();

    //when
    Message response = await(
      componentClient.forWorkflow(workflowId)
        .method(WorkflowWithDefaultRecoverStrategy::startFailingCounter)
        .invokeAsync(counterId));

    assertThat(response.text()).isEqualTo("workflow started");

    //then
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Integer counterValue = getFailingCounterValue(counterId);
        assertThat(counterValue).isEqualTo(3);
      });

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var state = await(
          componentClient.forWorkflow(workflowId)
            .method(WorkflowWithDefaultRecoverStrategy::get)
            .invokeAsync());

        assertThat(state.finished()).isTrue();
      });
  }

  @Test
  public void shouldRecoverFailingCounterWorkflowWithRecoverStrategy() {
    //given
    var counterId = randomId();
    var workflowId = randomId();

    //when
    Message response = await(
      componentClient.forWorkflow(workflowId)
        .method(WorkflowWithRecoverStrategy::startFailingCounter)
        .invokeAsync(counterId));

    assertThat(response.text()).isEqualTo("workflow started");

    //then
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Integer counterValue = getFailingCounterValue(counterId);
        assertThat(counterValue).isEqualTo(3);
      });

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var state = await(
          componentClient.forWorkflow(workflowId)
            .method(WorkflowWithRecoverStrategy::get)
            .invokeAsync());

        assertThat(state.finished()).isTrue();
      });
  }

  @Test
  public void shouldRecoverFailingCounterWorkflowWithRecoverStrategyAndAsyncCall() {
    //given
    var counterId = randomId();
    var workflowId = randomId();

    //when
    Message response = await(
      componentClient.forWorkflow(workflowId)
        .method(WorkflowWithRecoverStrategyAndAsyncCall::startFailingCounter)
        .invokeAsync(counterId));

    assertThat(response.text()).isEqualTo("workflow started");

    //then
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Integer counterValue = getFailingCounterValue(counterId);
        assertThat(counterValue).isEqualTo(3);
      });

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var state = await(
          componentClient.forWorkflow(workflowId)
            .method(WorkflowWithRecoverStrategyAndAsyncCall::get)
            .invokeAsync());
        assertThat(state.finished()).isTrue();
      });
  }

  @Test
  public void shouldRecoverWorkflowTimeout() {
    //given
    var counterId = randomId();
    var workflowId = randomId();

    //when
    Message response = await(
      componentClient.forWorkflow(workflowId)
        .method(WorkflowWithTimeout::startFailingCounter)
        .invokeAsync(counterId));

    assertThat(response.text()).isEqualTo("workflow started");

    //then
    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Integer counterValue = getFailingCounterValue(counterId);
        assertThat(counterValue).isEqualTo(3);
      });

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var state = await(
          componentClient.forWorkflow(workflowId)
            .method(WorkflowWithTimeout::get)
            .invokeAsync());
        assertThat(state.finished()).isTrue();
      });
  }

  @Test
  public void shouldRecoverWorkflowStepTimeout() {
    //given
    var counterId = randomId();
    var workflowId = randomId();

    //when
    Message response = await(
      componentClient.forWorkflow(workflowId)
        .method(WorkflowWithStepTimeout::startFailingCounter)
        .invokeAsync(counterId));

    assertThat(response.text()).isEqualTo("workflow started");

    //then
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var state = await(
          componentClient.forWorkflow(workflowId)
            .method(WorkflowWithStepTimeout::get)
            .invokeAsync());

        assertThat(state.value()).isEqualTo(2);
        assertThat(state.finished()).isTrue();
      });
  }

  @Test
  public void shouldUseTimerInWorkflowDefinition() {
    //given
    var counterId = randomId();
    var workflowId = randomId();

    //when
    Message response = await(
      componentClient.forWorkflow(workflowId)
        .method(WorkflowWithTimer::startFailingCounter)
        .invokeAsync(counterId));

    assertThat(response.text()).isEqualTo("workflow started");

    //then
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var state = await(
          componentClient.forWorkflow(workflowId)
            .method(WorkflowWithTimer::get)
            .invokeAsync());

        assertThat(state.finished()).isTrue();
        assertThat(state.value()).isEqualTo(12);
      });
  }


  @Test
  public void shouldNotUpdateWorkflowStateAfterEndTransition() {
    //given
    var workflowId = randomId();
    await(
      componentClient.forWorkflow(workflowId)
        .method(DummyWorkflow::startAndFinish)
        .invokeAsync()
    );
    assertThat(await(
      componentClient.forWorkflow(workflowId)
        .method(DummyWorkflow::get).invokeAsync())).isEqualTo(10);

    //when
    try {
      await(
        componentClient.forWorkflow(workflowId)
          .method(DummyWorkflow::update).invokeAsync());
    } catch (RuntimeException exception) {
      // ignore "500 Internal Server Error" exception from the proxy
    }

    //then
    assertThat(await(
      componentClient.forWorkflow(workflowId)
        .method(DummyWorkflow::get).invokeAsync())).isEqualTo(10);
  }

  @Test
  public void shouldRunWorkflowStepWithoutInitialState() {
    //given
    var workflowId = randomId();

    //when
    String response = await(componentClient.forWorkflow(workflowId)
      .method(WorkflowWithoutInitialState::start).invokeAsync());

    assertThat(response).contains("ok");

    //then
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var state = await(componentClient.forWorkflow(workflowId).method(WorkflowWithoutInitialState::get).invokeAsync());
        assertThat(state).contains("success");
      });
  }

  @Test
  public void shouldAllowHierarchyWorkflow() {
    var workflowId = randomId();
    await(componentClient.forWorkflow(workflowId)
      .method(TextWorkflow::setText).invokeAsync("some text"));


    var result = await(componentClient.forWorkflow(workflowId)
      .method(TextWorkflow::getText).invokeAsync());

    assertThat(result).isEqualTo(Optional.of("some text"));
  }

  @Test
  public void shouldBeCallableWithGenericParameter() {
    var workflowId = randomId();
    String response1 = await(componentClient.forWorkflow(workflowId)
      .method(TransferWorkflow::genericStringsCall)
      .invokeAsync(List.of("somestring"))).text();

    assertThat(response1).isEqualTo("genericCall ok");

    String response2 = await(componentClient.forWorkflow(workflowId)
      .method(TransferWorkflow::genericCall)
      .invokeAsync(List.of(new TransferWorkflow.SomeClass("somestring")))).text();

    assertThat(response2).isEqualTo("genericCall ok");
  }


  private String randomTransferId() {
    return randomId();
  }

  private static String randomId() {
    return UUID.randomUUID().toString();
  }

  private Integer getFailingCounterValue(String counterId) {
    return await(
      componentClient
        .forEventSourcedEntity(counterId)
        .method(FailingCounterEntity::get).invokeAsync(),
      Duration.ofSeconds(20));
  }

  private void createWallet(String walletId, int amount) {
    await(
      componentClient.forKeyValueEntity(walletId)
        .method(WalletEntity::create)
        .invokeAsync(amount));
  }

  private int getWalletBalance(String walletId) {
    return await(
      componentClient.forKeyValueEntity(walletId)
        .method(WalletEntity::get)
        .invokeAsync()
    ).value;
  }
}
