/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static akkajavasdk.components.workflowentities.TransferConsumer.TRANSFER_CONSUMER_STORE;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.CommandException;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.MyException;
import akkajavasdk.components.actions.echo.Message;
import akkajavasdk.components.views.TransferView;
import akkajavasdk.components.workflowentities.DummyTransferStore;
import akkajavasdk.components.workflowentities.DummyWorkflow;
import akkajavasdk.components.workflowentities.FailingCounterEntity;
import akkajavasdk.components.workflowentities.Transfer;
import akkajavasdk.components.workflowentities.TransferState;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        componentClient
            .forWorkflow(transferId)
            .method(TransferWorkflow::startTransfer)
            .invoke(transfer);

    assertThat(message.text()).isEqualTo("Transfer amount should be greater than zero");
  }

  @Test
  public void shouldTransferMoneyAndDelete() {
    var walletId1 = "1";
    var walletId2 = "2";
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
    var walletId1 = "1";
    var walletId2 = "2";
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
  public void shouldVerifyWorkflowSubscriptions() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId1 = randomTransferId();
    var transferId2 = randomTransferId();
    var transfer1 = new Transfer(walletId1, walletId2, 10);
    var transfer2 = new Transfer(walletId1, walletId2, 20);

    componentClient
        .forWorkflow(transferId1)
        .method(TransferWorkflow::startTransfer)
        .invoke(transfer1);
    componentClient
        .forWorkflow(transferId2)
        .method(TransferWorkflow::startTransfer)
        .invoke(transfer2);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var transferState1 = DummyTransferStore.get(TRANSFER_CONSUMER_STORE, transferId1);
              var transferState2 = DummyTransferStore.get(TRANSFER_CONSUMER_STORE, transferId2);

              assertThat(transferState1.transfer()).isEqualTo(transfer1);
              assertThat(transferState2.transfer()).isEqualTo(transfer2);

              var result = componentClient.forView().method(TransferView::getAll).invoke();
              assertThat(result.entries())
                  .contains(
                      new TransferView.TransferEntry(transferId1, true),
                      new TransferView.TransferEntry(transferId2, true));
            });

    componentClient.forWorkflow(transferId1).method(TransferWorkflow::delete).invoke();

    var state = componentClient.forWorkflow(transferId1).method(TransferWorkflow::get).invoke();
    assertThat(state).isEqualTo(TransferState.EMPTY);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var transferState1 = DummyTransferStore.get(TRANSFER_CONSUMER_STORE, transferId1);
              assertThat(transferState1).isNull();

              var result = componentClient.forView().method(TransferView::getAll).invoke();
              assertThat(result.entries())
                  .containsOnly(new TransferView.TransferEntry(transferId2, true));
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
    var walletId1 = "1";
    var walletId2 = "2";
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
    var walletId1 = "1";
    var walletId2 = "2";
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
    var walletId1 = "1";
    var walletId2 = "2";
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
    var walletId1 = "1";
    var walletId2 = "2";
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
  public void shouldUseTimerInWorkflowDefinition() {
    // given
    var counterId = randomId();
    var workflowId = randomId();

    // when
    Message response =
        componentClient
            .forWorkflow(workflowId)
            .method(WorkflowWithTimer::startFailingCounter)
            .invoke(counterId);

    assertThat(response.text()).isEqualTo("workflow started");

    // then
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var state =
                  componentClient.forWorkflow(workflowId).method(WorkflowWithTimer::get).invoke();

              assertThat(state.finished()).isTrue();
              assertThat(state.value()).isEqualTo(12);
            });
  }

  @Test
  public void shouldNotUpdateWorkflowStateAfterEndTransition() {
    // given
    var workflowId = randomId();
    componentClient.forWorkflow(workflowId).method(DummyWorkflow::startAndFinish).invoke();

    assertThat(componentClient.forWorkflow(workflowId).method(DummyWorkflow::get).invoke())
        .isEqualTo(10);

    // when
    try {

      componentClient.forWorkflow(workflowId).method(DummyWorkflow::update).invoke();
    } catch (RuntimeException exception) {
      // ignore "500 Internal Server Error" exception from the proxy
    }

    // then
    assertThat(componentClient.forWorkflow(workflowId).method(DummyWorkflow::get).invoke())
        .isEqualTo(10);
  }

  @Test
  public void shouldRunWorkflowStepWithoutInitialState() {
    // given
    var workflowId = randomId();

    // when
    String response =
        componentClient.forWorkflow(workflowId).method(WorkflowWithoutInitialState::start).invoke();

    assertThat(response).contains("ok");

    // then
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var state =
                  componentClient
                      .forWorkflow(workflowId)
                      .method(WorkflowWithoutInitialState::get)
                      .invoke();
              assertThat(state).contains("success");
            });
  }

  @Test
  public void shouldAllowHierarchyWorkflow() {
    var workflowId = randomId();
    componentClient.forWorkflow(workflowId).method(TextWorkflow::setText).invoke("some text");

    var result = componentClient.forWorkflow(workflowId).method(TextWorkflow::getText).invoke();

    assertThat(result).isEqualTo(Optional.of("some text"));
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

  @Test
  public void shouldTestExceptions() {
    var exc1 =
        Assertions.assertThrows(
            CommandException.class,
            () -> {
              componentClient.forWorkflow("1").method(TransferWorkflow::run).invoke("errorMessage");
            });
    assertThat(exc1.getMessage()).isEqualTo("errorMessage");

    var exc2 =
        Assertions.assertThrows(
            CommandException.class,
            () -> {
              componentClient
                  .forWorkflow("1")
                  .method(TransferWorkflow::run)
                  .invoke("errorCommandException");
            });
    assertThat(exc2.getMessage()).isEqualTo("errorCommandException");

    var exc3 =
        Assertions.assertThrows(
            MyException.class,
            () -> {
              componentClient
                  .forWorkflow("1")
                  .method(TransferWorkflow::run)
                  .invoke("errorMyException");
            });
    assertThat(exc3.getMessage()).isEqualTo("errorMyException");
    assertThat(exc3.getData()).isEqualTo(new MyException.SomeData("some data"));

    var exc4 =
        Assertions.assertThrows(
            MyException.class,
            () -> {
              componentClient
                  .forWorkflow("1")
                  .method(TransferWorkflow::run)
                  .invoke("throwMyException");
            });
    assertThat(exc4.getMessage()).isEqualTo("throwMyException");
    assertThat(exc4.getData()).isEqualTo(new MyException.SomeData("some data"));

    var exc5 =
        Assertions.assertThrows(
            RuntimeException.class,
            () -> {
              componentClient
                  .forWorkflow("1")
                  .method(TransferWorkflow::run)
                  .invoke("throwRuntimeException");
            });
    assertThat(exc5.getMessage())
        .contains("Unexpected failure: java.lang.RuntimeException: throwRuntimeException");
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
