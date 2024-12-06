package com.example.wallet.application;

import akka.javasdk.testkit.TestKitSupport;
import com.example.wallet.application.WalletEntity.WalletResult;
import com.example.wallet.domain.WalletCommand;
import org.junit.jupiter.api.Test;

import static com.example.transfer.TransferWorkflowIT.randomId;
import static org.assertj.core.api.Assertions.assertThat;

class WalletEntityIT extends TestKitSupport {

  @Test
  public void shouldDeduplicateWithdrawCommand() {
    // given
    var walletId = randomId();
    var withdraw = new WalletCommand.Withdraw(randomId(), 10);
    await(componentClient.forEventSourcedEntity(walletId)
      .method(WalletEntity::create)
      .invokeAsync(100));

    // when
    withdraw(walletId, withdraw);
    withdraw(walletId, withdraw);
    withdraw(walletId, withdraw);

    // then
    Integer balance = await(componentClient.forEventSourcedEntity(walletId)
      .method(WalletEntity::get)
      .invokeAsync());
    assertThat(balance).isEqualTo(100 - 10);
  }

  @Test
  public void shouldDeduplicateDepositCommand() {
    // given
    var walletId = randomId();
    var deposit = new WalletCommand.Deposit(randomId(), 10);
    await(componentClient.forEventSourcedEntity(walletId)
      .method(WalletEntity::create)
      .invokeAsync(100));

    // when
    deposit(walletId, deposit);
    deposit(walletId, deposit);
    deposit(walletId, deposit);

    // then
    Integer balance = await(componentClient.forEventSourcedEntity(walletId)
      .method(WalletEntity::get)
      .invokeAsync());
    assertThat(balance).isEqualTo(100 + 10);
  }

  private WalletResult deposit(String walletId, WalletCommand.Deposit deposit) {
    return await(componentClient.forEventSourcedEntity(walletId)
      .method(WalletEntity::deposit)
      .invokeAsync(deposit));
  }

  private WalletResult withdraw(String walletId, WalletCommand.Withdraw withdraw) {
    return await(componentClient.forEventSourcedEntity(walletId)
      .method(WalletEntity::withdraw)
      .invokeAsync(withdraw));
  }
}