package com.example.transfer;

import com.example.transfer.TransferState.Transfer;
import com.example.wallet.Ok;
import com.example.wallet.WalletEntity;
import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class TransferWorkflowIntegrationTest extends KalixIntegrationTestKitSupport {

  @Test
  public void shouldTransferMoney() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transfer = new Transfer(walletId1, walletId2, 10);

    String response =
      await(
        componentClient
          .forWorkflow(transferId)
          .method(TransferWorkflow::startTransfer)
          .invokeAsync(transfer)
      ).value();

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


  private String randomTransferId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private void createWallet(String walletId, int amount) {
    var res =
      await(
        componentClient
          .forKeyValueEntity(walletId)
          .method(WalletEntity::create)
          .invokeAsync(amount)
      );

    assertEquals(Ok.instance, res);
  }

  private int getWalletBalance(String walletId) {
    return await(
      componentClient
        .forKeyValueEntity(walletId)
        .method(WalletEntity::get).invokeAsync()
    );
  }

}