/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.actions.echo.Message;
import akkajavasdk.components.workflowentities.FailingCounterEntity;
import akkajavasdk.components.workflowentities.Transfer;
import akkajavasdk.components.workflowentities.WalletEntity;
import akkajavasdk.components.workflowentities.legacy.TransferWorkflow;
import akkajavasdk.components.workflowentities.legacy.TransferWorkflowWithFraudDetection;
import akkajavasdk.components.workflowentities.legacy.TransferWorkflowWithoutInputs;
import akkajavasdk.components.workflowentities.legacy.WorkflowWithDefaultRecoverStrategy;
import akkajavasdk.components.workflowentities.legacy.WorkflowWithRecoverStrategy;
import akkajavasdk.components.workflowentities.legacy.WorkflowWithRecoverStrategyAndAsyncCall;
import akkajavasdk.components.workflowentities.legacy.WorkflowWithStepTimeout;
import akkajavasdk.components.workflowentities.legacy.WorkflowWithTimeout;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class WorkflowLegacyApiTest extends TestKitSupport {

  @Test
  public void shouldNotStartTransferForWithNegativeAmount() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, -10);

    Message message =
        componentClient
            .forWorkflow(transferId)
            .method(TransferWorkflow::startTransfer)
            .invoke(transfer);

    assertThat(message.text()).isEqualTo("Transfer amount should be greater than zero");
  }

  @Test
  public void shouldTransferMoneyAndDelete() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    Message response =
        componentClient
            .forWorkflow(transferId)
            .method(TransferWorkflow::startTransfer)
            .invoke(transfer);

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var balance1 = getWalletBalance(walletId1);
              var balance2 = getWalletBalance(walletId2);

              assertThat(balance1).isEqualTo(90);
              assertThat(balance2).isEqualTo(110);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              // this is mostly to verify that the last step (Runnable + Supplier) worked as expect
              String lastStep =
                  componentClient
                      .forWorkflow(transferId)
                      .method(TransferWorkflow::getLastStep)
                      .invoke()
                      .text();
              assertThat(lastStep).isEqualTo("logAndStop");
            });

    var isDeleted =
        componentClient.forWorkflow(transferId).method(TransferWorkflow::hasBeenDeleted).invoke();
    assertThat(isDeleted).isFalse();

    componentClient.forWorkflow(transferId).method(TransferWorkflow::delete).invoke();

    var isDeletedAfterDeletion =
        componentClient.forWorkflow(transferId).method(TransferWorkflow::hasBeenDeleted).invoke();
    assertThat(isDeletedAfterDeletion).isTrue();
  }

  @Test
  public void shouldUpdateAndDelete() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    componentClient
        .forWorkflow(transferId)
        .method(TransferWorkflow::updateAndDelete)
        .invoke(transfer);

    var isDeleted =
        componentClient.forWorkflow(transferId).method(TransferWorkflow::hasBeenDeleted).invoke();
    assertThat(isDeleted).isTrue();
  }

  @Test
  public void shouldTransferMoneyWithoutStepInputs() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    Message response =
        componentClient
            .forWorkflow(transferId)
            .method(TransferWorkflowWithoutInputs::startTransfer)
            .invoke(transfer);

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var balance1 = getWalletBalance(walletId1);
              var balance2 = getWalletBalance(walletId2);

              assertThat(balance1).isEqualTo(90);
              assertThat(balance2).isEqualTo(110);
            });
  }

  @Test
  public void shouldTransferAsyncMoneyWithoutStepInputs() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    Message response =
        componentClient
            .forWorkflow(transferId)
            .method(TransferWorkflowWithoutInputs::startTransferAsync)
            .invoke(transfer);

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var balance1 = getWalletBalance(walletId1);
              var balance2 = getWalletBalance(walletId2);

              assertThat(balance1).isEqualTo(90);
              assertThat(balance2).isEqualTo(110);
            });
  }

  @Test
  public void shouldTransferMoneyWithFraudDetection() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    Message response =
        componentClient
            .forWorkflow(transferId)
            .method(TransferWorkflowWithFraudDetection::startTransfer)
            .invoke(transfer);

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var balance1 = getWalletBalance(walletId1);
              var balance2 = getWalletBalance(walletId2);

              assertThat(balance1).isEqualTo(90);
              assertThat(balance2).isEqualTo(110);
            });
  }

  @Test
  public void shouldTransferMoneyWithFraudDetectionAndManualAcceptance() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 100000);
    createWallet(walletId2, 100000);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 1000);

    Message response =
        componentClient
            .forWorkflow(transferId)
            .method(TransferWorkflowWithFraudDetection::startTransfer)
            .invoke(transfer);

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var transferState =
                  componentClient
                      .forWorkflow(transferId)
                      .method(TransferWorkflowWithFraudDetection::getTransferState)
                      .invoke();

              assertThat(transferState.finished()).isFalse();
              assertThat(transferState.accepted()).isFalse();
              assertThat(transferState.lastStep()).isEqualTo("fraud-detection");
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              Message acceptedResponse =
                  componentClient
                      .forWorkflow(transferId)
                      .method(TransferWorkflowWithFraudDetection::acceptTransfer)
                      .invoke();

              assertThat(acceptedResponse.text()).isEqualTo("transfer accepted");
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var balance1 = getWalletBalance(walletId1);
              var balance2 = getWalletBalance(walletId2);

              assertThat(balance1).isEqualTo(99000);
              assertThat(balance2).isEqualTo(101000);
            });
  }

  @Test
  public void shouldNotTransferMoneyWhenFraudDetectionRejectTransfer() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 1000000);

    Message response =
        componentClient
            .forWorkflow(transferId)
            .method(TransferWorkflowWithFraudDetection::startTransfer)
            .invoke(transfer);

    assertThat(response.text()).contains("transfer started");

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var balance1 = getWalletBalance(walletId1);
              var balance2 = getWalletBalance(walletId2);

              assertThat(balance1).isEqualTo(100);
              assertThat(balance2).isEqualTo(100);

              var transferState =
                  componentClient
                      .forWorkflow(transferId)
                      .method(TransferWorkflowWithFraudDetection::getTransferState)
                      .invoke();

              assertThat(transferState.finished()).isTrue();
              assertThat(transferState.accepted()).isFalse();
              assertThat(transferState.lastStep()).isEqualTo("fraud-detection");
            });
  }

  @Test
  public void shouldRecoverFailingCounterWorkflowWithDefaultRecoverStrategy() {
    // given
    var counterId = randomId();
    var workflowId = randomId();

    // when
    Message response =
        componentClient
            .forWorkflow(workflowId)
            .method(WorkflowWithDefaultRecoverStrategy::startFailingCounter)
            .invoke(counterId);

    assertThat(response.text()).isEqualTo("workflow started");

    // then
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              Integer counterValue = getFailingCounterValue(counterId);
              assertThat(counterValue).isEqualTo(3);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var state =
                  componentClient
                      .forWorkflow(workflowId)
                      .method(WorkflowWithDefaultRecoverStrategy::get)
                      .invoke();

              assertThat(state.finished()).isTrue();
            });
  }

  @Test
  public void shouldRecoverFailingCounterWorkflowWithRecoverStrategy() {
    // given
    var counterId = randomId();
    var workflowId = randomId();

    // when
    Message response =
        componentClient
            .forWorkflow(workflowId)
            .method(WorkflowWithRecoverStrategy::startFailingCounter)
            .invoke(counterId);

    assertThat(response.text()).isEqualTo("workflow started");

    // then
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              Integer counterValue = getFailingCounterValue(counterId);
              assertThat(counterValue).isEqualTo(3);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var state =
                  componentClient
                      .forWorkflow(workflowId)
                      .method(WorkflowWithRecoverStrategy::get)
                      .invoke();

              assertThat(state.finished()).isTrue();
            });
  }

  @Test
  public void shouldRecoverFailingCounterWorkflowWithRecoverStrategyAndAsyncCall() {
    // given
    var counterId = randomId();
    var workflowId = randomId();

    // when
    Message response =
        componentClient
            .forWorkflow(workflowId)
            .method(WorkflowWithRecoverStrategyAndAsyncCall::startFailingCounter)
            .invoke(counterId);

    assertThat(response.text()).isEqualTo("workflow started");

    // then
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              Integer counterValue = getFailingCounterValue(counterId);
              assertThat(counterValue).isEqualTo(3);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var state =
                  await(
                      componentClient
                          .forWorkflow(workflowId)
                          .method(WorkflowWithRecoverStrategyAndAsyncCall::get)
                          .invokeAsync());
              assertThat(state.finished()).isTrue();
            });
  }

  @Test
  public void shouldRecoverWorkflowTimeout() {
    // given
    var counterId = randomId();
    var workflowId = randomId();

    // when
    Message response =
        componentClient
            .forWorkflow(workflowId)
            .method(WorkflowWithTimeout::startFailingCounter)
            .invoke(counterId);

    assertThat(response.text()).isEqualTo("workflow started");

    // then
    Awaitility.await()
        .ignoreExceptions()
        .atMost(15, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              Integer counterValue = getFailingCounterValue(counterId);
              assertThat(counterValue).isEqualTo(3);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var state =
                  componentClient.forWorkflow(workflowId).method(WorkflowWithTimeout::get).invoke();
              assertThat(state.finished()).isTrue();
            });
  }

  @Test
  public void shouldRecoverWorkflowStepTimeout() {
    // given
    var counterId = randomId();
    var workflowId = randomId();

    // when
    Message response =
        componentClient
            .forWorkflow(workflowId)
            .method(WorkflowWithStepTimeout::startFailingCounter)
            .invoke(counterId);

    assertThat(response.text()).isEqualTo("workflow started");

    // then
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var state =
                  componentClient
                      .forWorkflow(workflowId)
                      .method(WorkflowWithStepTimeout::get)
                      .invoke();

              assertThat(state.value()).isEqualTo(2);
              assertThat(state.finished()).isTrue();
            });
  }

  @Test
  public void shouldBeCallableWithGenericParameter() {
    var workflowId = randomId();
    String response1 =
        componentClient
            .forWorkflow(workflowId)
            .method(TransferWorkflow::genericStringsCall)
            .invoke(List.of("somestring"))
            .text();

    assertThat(response1).isEqualTo("genericCall ok");

    String response2 =
        componentClient
            .forWorkflow(workflowId)
            .method(TransferWorkflow::genericCall)
            .invoke(List.of(new TransferWorkflow.SomeClass("somestring")))
            .text();

    assertThat(response2).isEqualTo("genericCall ok");
  }

  @Test
  public void commandHandlerShouldBeRunningOnVirtualThread() {
    var result =
        componentClient
            .forWorkflow(randomId())
            .method(TransferWorkflow::commandHandlerIsOnVirtualThread)
            .invoke();
    assertThat(result).isTrue();
  }

  private String randomTransferId() {
    return randomId();
  }

  private static String randomId() {
    return UUID.randomUUID().toString();
  }

  private Integer getFailingCounterValue(String counterId) {
    return componentClient
        .forEventSourcedEntity(counterId)
        .method(FailingCounterEntity::get)
        .invoke();
  }

  private void createWallet(String walletId, int amount) {
    componentClient.forKeyValueEntity(walletId).method(WalletEntity::create).invoke(amount);
  }

  private int getWalletBalance(String walletId) {
    return componentClient.forKeyValueEntity(walletId).method(WalletEntity::get).invoke().value;
  }
}
