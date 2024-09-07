package com.example.wallet.domain;

public record Wallet(String id, int balance) {
    public Wallet withdrawn(int amount) {
        return new Wallet(id, balance - amount);
    }
    public Wallet deposited(int amount) {
        return new Wallet(id, balance + amount);
    }
    public Wallet created(int initialAmount) {
        return new Wallet(id,  initialAmount);
    }


}
