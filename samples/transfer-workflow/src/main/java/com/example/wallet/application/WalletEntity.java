package com.example.wallet.application;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import akka.javasdk.annotations.ComponentId;
import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletCmd;
import com.example.wallet.domain.WalletEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import akka.Done;

// tag::wallet[]
@ComponentId("wallet")
public class WalletEntity extends EventSourcedEntity<Wallet, WalletEvent> {
  
  private final String entityId;

  // end::wallet[]
  private static final Logger logger = LoggerFactory.getLogger(WalletEntity.class);

  public WalletEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public Wallet emptyState(){
    return new Wallet(entityId,0);
  }

  // tag::wallet[]
  public Effect<Done> create(WalletCmd.CreateCmd cmd) {
    var event = new WalletEvent.Created(cmd.initialAmount());
    return effects().persist(event).thenReply(newState -> Done.done());
  }

  public Effect<Done> withdraw(WalletCmd.WithdrawCmd cmd) {
    Wallet updatedWallet = currentState().withdrawn(cmd.amount());
    if (updatedWallet.balance() < 0) {
      return effects().error("Insufficient balance");
    } else {
      // end::wallet[]
      logger.info("Withdraw walletId: [{}] amount -{} balance after {}", currentState().id(), cmd.amount(), updatedWallet.balance());
      // tag::wallet[]

      var event = new WalletEvent.Withdrawn(cmd.amount());
      return effects().persist(event).thenReply(newState -> Done.done());
    }
  }

  public Effect<Done> deposit(WalletCmd.DepositCmd cmd) {
    Wallet updatedWallet = currentState().deposited(cmd.amount());
    // end::wallet[]
    logger.info("Deposit walletId: [{}] amount +{} balance after {}", currentState().id(), cmd.amount(), updatedWallet.balance());
    // tag::wallet[]

    var event = new WalletEvent.Deposited(cmd.amount());
    return effects().persist(event).thenReply(newState -> Done.done());
  }

  public Effect<Integer> get() {
    return effects().reply(currentState().balance());
  }

  @Override
  public Wallet applyEvent(WalletEvent event) {
    return switch (event) {
      case WalletEvent.Deposited dep -> currentState().deposited(dep.amount());
      case WalletEvent.Withdrawn with -> currentState().withdrawn(with.amount());
      case WalletEvent.Created con -> currentState().created(con.initialAmount());
    };
  }
}
// end::wallet[]
