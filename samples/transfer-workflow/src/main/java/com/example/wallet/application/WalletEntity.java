package com.example.wallet.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.wallet.domain.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static akka.Done.done;

// tag::wallet[]
@ComponentId("wallet")
public class WalletEntity extends KeyValueEntity<Wallet> {

  // end::wallet[]
  private static final Logger logger = LoggerFactory.getLogger(WalletEntity.class);

  // tag::wallet[]
  public Effect<Done> create(int initialBalance) { // <1>
    return effects().updateState(new Wallet(commandContext().entityId(), initialBalance)).thenReply(done());
  }

  public Effect<Done> withdraw(int amount) { // <2>
    if (currentState().balance() < amount) {
      return effects().error("Insufficient balance");
    } else {
      Wallet updateWallet = currentState().withdraw(amount);
      // end::wallet[]
      logger.info("Withdraw walletId: [{}] amount -{} balance after {}", currentState().id(), amount, updateWallet.balance());
      // tag::wallet[]
      return effects().updateState(updateWallet).thenReply(done());
    }
  }

  public Effect<Done> deposit(int amount) { // <3>
    Wallet updateWallet = currentState().deposit(amount);
    // end::wallet[]
    logger.info("Deposit walletId: [{}] amount +{} balance after {}", currentState().id(), amount, updateWallet.balance());
    // tag::wallet[]
    return effects().updateState(updateWallet).thenReply(done());
  }

  public Effect<Integer> get() { // <4>
    return effects().reply(currentState().balance());
  }
}
// end::wallet[]
