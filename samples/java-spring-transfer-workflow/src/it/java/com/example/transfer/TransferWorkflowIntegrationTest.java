package com.example.transfer;

import com.example.Main;
import com.example.transfer.TransferState.Transfer;
import com.example.wallet.Ok;
import com.example.wallet.WalletEntity;
import kalix.javasdk.client.ComponentClient;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringBootTest(classes = Main.class)
public class TransferWorkflowIntegrationTest extends KalixIntegrationTestKitSupport {

  @Autowired
  private ComponentClient componentClient;

  @Autowired
  private WebClient webClient;

  private Duration timeout = Duration.of(10, SECONDS);

  @Test
  public void shouldTransferMoney() {
    var walletId1 = "1";
    var walletId2 = "2";
    createWallet(walletId1, 100);
    createWallet(walletId2, 100);
    var transferId = randomTransferId();
    var transferUrl = "/transfer/" + transferId;
    var transfer = new Transfer(walletId1, walletId2, 10);

    String response = webClient.put().uri(transferUrl)
      .bodyValue(transfer)
      .retrieve()
      .bodyToMono(Message.class)
      .map(Message::value)
      .block(timeout);

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
          .forValueEntity(walletId)
          .methodRef(WalletEntity::create)
          .invokeAsync(amount)
      );

    assertEquals(Ok.instance, res);
  }

  private int getWalletBalance(String walletId) {
    return await(
      componentClient
        .forValueEntity(walletId)
        .methodRef(WalletEntity::get).invokeAsync()
    );
  }

}