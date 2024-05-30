package com.example.wallet;

import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::wallet[]
@TypeId("wallet")
public class WalletEntity extends ValueEntity<WalletEntity.Wallet> {



  public record Wallet(String id, int balance) {
    public Wallet withdraw(int amount) {
      return new Wallet(id, balance - amount);
    }
    public Wallet deposit(int amount) {
      return new Wallet(id, balance + amount);
    }
  }

  // end::wallet[]

  private static final Logger logger = LoggerFactory.getLogger(WalletEntity.class);
  // tag::wallet[]
  public Effect<Ok> create(int initBalance) {
    var id = commandContext().entityId();
    return effects().updateState(new Wallet(id, initBalance)).thenReply(Ok.instance);
  }

  public Effect<Ok> withdraw(int amount) {
    Wallet updatedWallet = currentState().withdraw(amount);
    if (updatedWallet.balance < 0) {
      return effects().error("Insufficient balance");
    } else {
      // end::wallet[]
      logger.info("Withdraw walletId: [{}] amount -{} balance after {}", currentState().id(), amount, updatedWallet.balance());
      // tag::wallet[]
      return effects().updateState(updatedWallet).thenReply(Ok.instance);
    }
  }

  public Effect<Ok> deposit(int amount) {
    Wallet updatedWallet = currentState().deposit(amount);
    // end::wallet[]
    logger.info("Deposit walletId: [{}] amount +{} balance after {}", currentState().id(), amount, updatedWallet.balance());
    // tag::wallet[]
    return effects().updateState(updatedWallet).thenReply(Ok.instance);
  }

  public Effect<Integer> get() {
    return effects().reply(currentState().balance());
  }
}
// end::wallet[]
