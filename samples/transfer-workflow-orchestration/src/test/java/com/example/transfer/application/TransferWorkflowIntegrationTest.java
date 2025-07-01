package com.example.transfer.application;

import akka.javasdk.testkit.TestKitSupport;
import com.example.transfer.domain.Transfer;
import com.example.transfer.domain.TransferState;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.example.transfer.domain.TransferState.TransferStatus.WAITING_FOR_ACCEPTANCE;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class TransferWorkflowIntegrationTest extends TestKitSupport {

  @Test
  public void shouldTransferMoney() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    var transferId = randomId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    var walletService = getDependency(WalletService.class);
    walletService.deposit(walletId1, 100);
    walletService.deposit(walletId2, 100);

    var response = componentClient
      .forWorkflow(transferId)
      .method(TransferWorkflow::start)
      .invoke(transfer);

    assertThat(response).isEqualTo("transfer started");

    Awaitility.await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var wallet1Balance = walletService.getBalance(walletId1);
        var wallet2Balance = walletService.getBalance(walletId2);

        assertThat(wallet1Balance).isEqualTo(90);
        assertThat(wallet2Balance).isEqualTo(110);
      });
  }

  @Test
  public void shouldTransferMoneyWithAcceptance() {
    var walletId1 = randomId();
    var walletId2 = randomId();
    var transferId = randomId();
    var transfer = new Transfer(walletId1, walletId2, 1001);

    var walletService = getDependency(WalletService.class);
    walletService.deposit(walletId1, 10000);
    walletService.deposit(walletId2, 10000);

    String response = componentClient
      .forWorkflow(transferId)
      .method(TransferWorkflow::start)
      .invoke(transfer);

    assertThat(response).isEqualTo("transfer started");

    TransferState transferState =
      componentClient
        .forWorkflow(transferId)
        .method(TransferWorkflow::get)
        .invoke();

    assertThat(transferState.status()).isEqualTo(WAITING_FOR_ACCEPTANCE);

    componentClient
      .forWorkflow(transferId)
      .method(TransferWorkflow::accept)
      .invoke();

    Awaitility.await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var wallet1Balance = walletService.getBalance(walletId1);
        var wallet2Balance = walletService.getBalance(walletId2);

        assertThat(wallet1Balance).isEqualTo(8999);
        assertThat(wallet2Balance).isEqualTo(11001);
      });
  }

  public static String randomId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

}