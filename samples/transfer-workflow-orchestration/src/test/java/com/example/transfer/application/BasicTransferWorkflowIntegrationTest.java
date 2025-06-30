package com.example.transfer.application;

import akka.javasdk.testkit.TestKitSupport;
import com.example.transfer.domain.Transfer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;


public class BasicTransferWorkflowIntegrationTest extends TestKitSupport {

  @Test
  public void shouldTransferMoney() {
    var walletId1 = "w1";
    var walletId2 = "w2";
    var transferId = "t1";
    var transfer = new Transfer(walletId1, walletId2, 10);

    var walletService = getDependency(WalletService.class);
    walletService.deposit(walletId1, 100);
    walletService.deposit(walletId2, 100);

    var response = componentClient
          .forWorkflow(transferId)
          .method(BasicTransferWorkflow::start)
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


}
