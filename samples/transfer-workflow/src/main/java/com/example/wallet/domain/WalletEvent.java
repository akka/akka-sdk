package com.example.wallet.domain;

import akka.javasdk.annotations.TypeName;

// tag::event[]
public sealed interface WalletEvent {

  @TypeName("created")
  record Created(int initialBalance) implements WalletEvent {
  }

  @TypeName("withdrawn")
  record Withdrawn(int amount) implements WalletEvent {
  }

  @TypeName("deposited")
  record Deposited(int amount) implements WalletEvent {
  }

}
// end::event[]
