package com.example.wallet.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface WalletEvent {

  @TypeName("created")
  record Created(String walletId, int initialBalance) implements WalletEvent {
  }

  @TypeName("withdrawn")
  record Withdrawn(String commandId, int amount) implements WalletEvent {
  }

  @TypeName("deposited")
  record Deposited(String commandId, int amount) implements WalletEvent {
  }

}
