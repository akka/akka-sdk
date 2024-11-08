package com.example.wallet.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.example.wallet.application.WalletEntity.WalletResult.Failure;
import com.example.wallet.application.WalletEntity.WalletResult.Success;
import com.example.wallet.domain.Wallet;
import com.example.wallet.domain.WalletEvent;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static akka.Done.done;

// tag::wallet[]
@ComponentId("wallet")
public class WalletEntity extends EventSourcedEntity<Wallet, WalletEvent> {

  // end::wallet[]
  private static final Logger logger = LoggerFactory.getLogger(WalletEntity.class);

  @Override
  public Wallet applyEvent(WalletEvent event) {
    return switch(event) {
      case WalletEvent.Created c -> new Wallet(eventContext().entityId(), c.initialBalance());
      case WalletEvent.Withdrawn w -> currentState().withdraw(w.amount());
      case WalletEvent.Deposited d -> currentState().deposit(d.amount());
    };
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Success.class, name = "success"),
    @JsonSubTypes.Type(value = Failure.class, name = "failure")})
  public sealed interface WalletResult {
    record Failure(String errorMsg) implements WalletResult {
    }

    record Success() implements WalletResult {
    }
  }

  // tag::wallet[]
  public Effect<Done> create(int initialBalance) { // <1>
    if (currentState() != null){
      return effects().error("Wallet already exists");
    } else {
      return effects().persist(new WalletEvent.Created(initialBalance))
        .thenReply(__ -> done());
    }
  }

  public Effect<WalletResult> withdraw(int amount) { // <2>
    if (currentState() == null){
      return effects().error("Wallet does not exist");
    } else if (currentState().balance() < amount) {
      return effects().reply(new Failure("Insufficient balance"));
    } else {
      // end::wallet[]
      logger.info("Withdraw walletId: [{}] amount -{}", currentState().id(), amount);
      // tag::wallet[]
      return effects().persist(new WalletEvent.Withdrawn(amount))
          .thenReply(__ -> new WalletResult.Success());
    }
  }

  public Effect<WalletResult> deposit(int amount) { // <3>
    if (currentState() == null){
      return effects().error("Wallet does not exist");
    } else if (currentState() == null) {
      return effects().reply(new Failure("Wallet [" + commandContext().entityId() + "] not exists"));
    } else {
      // end::wallet[]
      logger.info("Deposit walletId: [{}] amount +{}", currentState().id(), amount);
      // tag::wallet[]
      return effects().persist(new WalletEvent.Deposited(amount))
          .thenReply(__ -> new WalletResult.Success());
    }
  }

  public Effect<Integer> get() { // <4>
    if (currentState() == null){
      return effects().error("Wallet does not exist");
    } else {
      return effects().reply(currentState().balance());
    }
  }
}
// end::wallet[]
