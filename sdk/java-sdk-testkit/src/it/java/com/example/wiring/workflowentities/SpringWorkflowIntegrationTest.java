/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import com.example.wiring.actions.echo.Message;
import akka.platform.javasdk.testkit.KalixTestKit;
import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofMillis;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class SpringWorkflowIntegrationTest extends KalixIntegrationTestKitSupport {

    @Override
    protected KalixTestKit.Settings kalixTestKitSettings() {
        return KalixTestKit.Settings.DEFAULT
                .withWorkflowTickInterval(ofMillis(500));
    }

    @Test
  public void shouldNotStartTransferForWithNegativeAmount() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, -10);

      Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(() -> {
          Message message =
            await(
              componentClient.forWorkflow(transferId)
                .method(TransferWorkflow::startTransfer)
                .invokeAsync(transfer));

          assertThat(message.text()).isEqualTo("Transfer amount should be greater than zero");
        });
  }

  @Test
  public void shouldTransferMoney() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);


    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Message response =
          await(componentClient.forWorkflow(transferId)
            .method(TransferWorkflow::startTransfer)
            .invokeAsync(transfer));

        assertThat(response.text()).contains("transfer started");
      });


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
  public void shouldTransferMoneyWithoutStepInputs() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Message response = await(
          componentClient.forWorkflow(transferId)
            .method(TransferWorkflowWithoutInputs::startTransfer)
            .invokeAsync(transfer));

        assertThat(response.text()).contains("transfer started");
      });

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


    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Message response = await(
          componentClient.forWorkflow(transferId)
            .method(TransferWorkflowWithoutInputs::startTransferAsync)
            .invokeAsync(transfer));

        assertThat(response.text()).contains("transfer started");
      });

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


    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Message response = await(
          componentClient.forWorkflow(transferId)
            .method(TransferWorkflowWithFraudDetection::startTransfer)
            .invokeAsync(transfer));

        assertThat(response.text()).contains("transfer started");
      });

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


    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Message response = await(
          componentClient.forWorkflow(transferId)
            .method(TransferWorkflowWithFraudDetection::startTransfer)
            .invokeAsync(transfer));

        assertThat(response.text()).contains("transfer started");
      });


    Awaitility.await()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {

        var transferState = await(
          componentClient.forWorkflow(transferId)
            .method(TransferWorkflowWithFraudDetection::getTransferState)
            .invokeAsync());

        assertThat(transferState.finished).isFalse();
        assertThat(transferState.accepted).isFalse();
        assertThat(transferState.lastStep).isEqualTo("fraud-detection");
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


    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
          Message response = await(
            componentClient.forWorkflow(transferId)
              .method(TransferWorkflowWithFraudDetection::startTransfer)
              .invokeAsync(transfer));

          assertThat(response.text()).contains("transfer started");
        });

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

        assertThat(transferState.finished).isTrue();
        assertThat(transferState.accepted).isFalse();
        assertThat(transferState.lastStep).isEqualTo("fraud-detection");
      });
  }

  @Test
  public void shouldRecoverFailingCounterWorkflowWithDefaultRecoverStrategy() {
    //given
    var counterId = randomId();
    var workflowId = randomId();

    //when
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Message response = await(
          componentClient.forWorkflow(workflowId)
            .method(WorkflowWithDefaultRecoverStrategy::startFailingCounter)
            .invokeAsync(counterId));

        assertThat(response.text()).isEqualTo("workflow started");
      });

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
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Message response = await(
          componentClient.forWorkflow(workflowId)
            .method(WorkflowWithRecoverStrategy::startFailingCounter)
            .invokeAsync(counterId));

        assertThat(response.text()).isEqualTo("workflow started");
      });

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
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Message response = await(
          componentClient.forWorkflow(workflowId)
            .method(WorkflowWithRecoverStrategyAndAsyncCall::startFailingCounter)
            .invokeAsync(counterId));

        assertThat(response.text()).isEqualTo("workflow started");
      });

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
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
          Message response = await(
            componentClient.forWorkflow(workflowId)
              .method(WorkflowWithTimeout::startFailingCounter)
              .invokeAsync(counterId));

          assertThat(response.text()).isEqualTo("workflow started");
        });

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
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
          Message response = await(
            componentClient.forWorkflow(workflowId)
              .method(WorkflowWithStepTimeout::startFailingCounter)
              .invokeAsync(counterId));

          assertThat(response.text()).isEqualTo("workflow started");
        });

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
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
          Message response = await(
            componentClient.forWorkflow(workflowId)
              .method(WorkflowWithTimer::startFailingCounter)
              .invokeAsync(counterId));

          assertThat(response.text()).isEqualTo("workflow started");
        });

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


  private String randomTransferId() {
    return randomId();
  }

  private static String randomId() {
    return UUID.randomUUID().toString().substring(0, 8);
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
      componentClient.forValueEntity(walletId)
        .method(WalletEntity::create)
        .invokeAsync(amount));
  }

  private int getWalletBalance(String walletId) {
    return await(
      componentClient.forValueEntity(walletId)
        .method(WalletEntity::get)
        .invokeAsync()
    ).value;
  }
}
