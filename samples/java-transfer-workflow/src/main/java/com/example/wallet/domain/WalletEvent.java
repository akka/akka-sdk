package com.example.wallet.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface WalletEvent {

    @TypeName("deposited")
    record Deposited(int amount) implements WalletEvent {}

    @TypeName("withdrawn")
    record Withdrawn(int amount) implements WalletEvent {}

    @TypeName("created")
    record Created(int initialAmount) implements WalletEvent {}

}
