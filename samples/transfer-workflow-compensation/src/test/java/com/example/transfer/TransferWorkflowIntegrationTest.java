package com.example.transfer;

import akka.javasdk.testkit.TestKitSupport;
import com.example.transfer.domain.TransferState;
import com.example.transfer.domain.TransferState.Transfer;
import com.example.transfer.application.TransferWorkflow;
import com.example.wallet.application.WalletEntity;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static akka.Done.done;
import static com.example.transfer.domain.TransferState.TransferStatus.COMPENSATION_COMPLETED;
import static com.example.transfer.domain.TransferState.TransferStatus.REQUIRES_MANUAL_INTERVENTION;
import static com.example.transfer.domain.TransferState.TransferStatus.TRANSFER_ACCEPTANCE_TIMED_OUT;
import static com.example.transfer.domain.TransferState.TransferStatus.WAITING_FOR_ACCEPTANCE;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;


public class TransferWorkflowIntegrationTest extends TestKitSupport {

  @Test
  public void shouldTransferMoney() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    String response = 
      componentClient
      .forWorkflow(transferId)
      .method(TransferWorkflow::startTransfer)
      .invoke(transfer);

    assertThat(response).isEqualTo("transfer started");

    Awaitility.await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var balance1 = getWalletBalance(walletId1);
        var balance2 = getWalletBalance(walletId2);

        assertThat(balance1).isEqualTo(90);
        assertThat(balance2).isEqualTo(110);
      });
  }

  @Test
  public void shouldTransferMoneyWithAcceptance() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 2000);
    createWallet(walletId2, 100);
    var transferId = randomId();
    var transfer = new Transfer(walletId1, walletId2, 1001);

    String response = componentClient
      .forWorkflow(transferId)
      .method(TransferWorkflow::startTransfer)
      .invoke(transfer);
    assertThat(response).isEqualTo("transfer started");

    assertThat(getTransferState(transferId).status()).isEqualTo(WAITING_FOR_ACCEPTANCE);

    String acceptanceResponse =
      componentClient
      .forWorkflow(transferId)
      .method(TransferWorkflow::accept).invoke();

    assertThat(acceptanceResponse).isEqualTo("transfer accepted");

    Awaitility.await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var balance1 = getWalletBalance(walletId1);
        var balance2 = getWalletBalance(walletId2);

        assertThat(balance1).isEqualTo(999);
        assertThat(balance2).isEqualTo(1101);
      });
  }

  @Test
  public void shouldTimeoutTransferAcceptance() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 2000);
    createWallet(walletId2, 100);
    var transferId = randomId();
    var transfer = new Transfer(walletId1, walletId2, 1001);

    String response = componentClient
      .forWorkflow(transferId)
      .method(TransferWorkflow::startTransfer)
      .invoke(transfer);
    assertThat(response).isEqualTo("transfer started");

    assertThat(getTransferState(transferId).status()).isEqualTo(WAITING_FOR_ACCEPTANCE);

    String acceptanceResponse = componentClient
      .forWorkflow(transferId)
      .method(TransferWorkflow::acceptanceTimeout)
      .invoke();
    assertThat(acceptanceResponse).contains("timed out");

    var balance1 = getWalletBalance(walletId1);
    var balance2 = getWalletBalance(walletId2);
    assertThat(balance1).isEqualTo(2000);
    assertThat(balance2).isEqualTo(100);

    assertThat(getTransferState(transferId).status()).isEqualTo(TRANSFER_ACCEPTANCE_TIMED_OUT);
  }

  @Test
  public void shouldCompensateFailedMoneyTransfer() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    createWallet(walletId1, 100);
    var transferId = randomId();
    var transfer = new Transfer(walletId1, walletId2, 10); //walletId2 not exists

    String response = componentClient
      .forWorkflow(transferId)
      .method(TransferWorkflow::startTransfer)
      .invoke(transfer);

    assertThat(response).isEqualTo("transfer started");

    Awaitility.await()
      .atMost(10, TimeUnit.of(SECONDS))
      .ignoreExceptions()
      .untilAsserted(() -> {
        TransferState transferState = getTransferState(transferId);
        assertThat(transferState.status()).isEqualTo(COMPENSATION_COMPLETED);

        var balance1 = getWalletBalance(walletId1);

        assertThat(balance1).isEqualTo(100);
      });
  }

  @Test
  public void shouldTimedOutTransferWorkflow() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    var transferId = randomId();
    var transfer = new Transfer(walletId1, walletId2, 10); //both not exists

    String response = componentClient
      .forWorkflow(transferId)
      .method(TransferWorkflow::startTransfer)
      .invoke(transfer);

    assertThat(response).isEqualTo("transfer started");

    Awaitility.await()
      .atMost(10, TimeUnit.of(SECONDS))
      .ignoreExceptions()
      .untilAsserted(() -> {
        TransferState transferState = getTransferState(transferId);
        assertThat(transferState.status()).isEqualTo(REQUIRES_MANUAL_INTERVENTION);
      });
  }


  public static String randomId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private void createWallet(String walletId, int amount) {
    var response = 
      componentClient
        .forEventSourcedEntity(walletId)
        .method(WalletEntity::create)
        .invoke(amount);

    assertThat(response).isEqualTo(done());
  }

  private int getWalletBalance(String walletId) {
    return 
      componentClient
        .forEventSourcedEntity(walletId)
        .method(WalletEntity::get)
        .invoke();
  }

  private TransferState getTransferState(String transferId) {
    return 
      componentClient
        .forWorkflow(transferId)
        .method(TransferWorkflow::getTransferState)
        .invoke();
  }

}
