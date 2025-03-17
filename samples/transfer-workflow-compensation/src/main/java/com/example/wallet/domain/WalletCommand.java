package com.example.wallet.domain;

// tag::deduplication[]
public sealed interface WalletCommand {

  String commandId();

  record Withdraw(String commandId, int amount) implements WalletCommand { // <1>
  }
  record Deposit(String commandId, int amount) implements WalletCommand { // <1>
  }
}
// end::deduplication[]
