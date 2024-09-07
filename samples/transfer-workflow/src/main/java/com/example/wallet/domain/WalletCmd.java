package com.example.wallet.domain;

public sealed interface WalletCmd {
    record CreateCmd(int initialAmount) implements WalletCmd {}
    record DepositCmd(int amount) implements WalletCmd {}
    record WithdrawCmd(int amount) implements WalletCmd {}
}