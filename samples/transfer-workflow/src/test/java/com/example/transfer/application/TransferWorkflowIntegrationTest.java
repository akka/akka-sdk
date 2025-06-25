package com.example.transfer.application;

import akka.javasdk.testkit.TestKitSupport;
import com.example.transfer.application.TransfersView.TransferEntries;
import com.example.transfer.domain.TransferState.Transfer;
import com.example.wallet.application.WalletEntity;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static akka.Done.done;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class TransferWorkflowIntegrationTest extends TestKitSupport {

  @Test
  public void shouldTransferMoney() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    var response =

        componentClient
          .forWorkflow(transferId)
          .method(TransferWorkflow::startTransfer)
          .invoke(transfer);

    assertThat(response).isEqualTo(done());

    Awaitility.await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var balance1 = getWalletBalance(walletId1);
        var balance2 = getWalletBalance(walletId2);

        assertThat(balance1).isEqualTo(90);
        assertThat(balance2).isEqualTo(110);

        TransferEntries result = componentClient.forView().method(TransfersView::getAllCompleted).invoke();
        assertThat(result.entries()).contains(new TransfersView.TransferEntry(transferId, "COMPLETED"));
      });
  }


  private String randomTransferId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private void createWallet(String walletId, int amount) {
    var res =

        componentClient
          .forEventSourcedEntity(walletId)
          .method(WalletEntity::create)
          .invoke(amount);

    assertEquals(done(), res);
  }

  private int getWalletBalance(String walletId) {
    return
      componentClient
        .forEventSourcedEntity(walletId)
        .method(WalletEntity::get).invoke();
  }

}
