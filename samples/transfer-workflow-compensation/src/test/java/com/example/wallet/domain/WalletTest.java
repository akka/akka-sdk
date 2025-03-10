package com.example.wallet.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WalletTest {

  @Test
  public void shouldLimitCommandIdsSize() {
    //given
    Wallet wallet = new Wallet("w1", 100, new LinkedHashSet<>());

    //when
    for (int i = 0; i < 10000; i++) {
      List<WalletEvent> events = wallet.handle(new WalletCommand.Deposit(UUID.randomUUID().toString(), 10));
      wallet = wallet.applyEvent(events.get(0));
    }

    //then
    assertThat(wallet.commandIds()).hasSize(1000);
  }
}