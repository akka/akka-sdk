package com.example.wallet.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.wallet.application.WalletEntity.WalletResult.Failure;
import com.example.wallet.application.WalletEntity.WalletResult.Success;
import com.example.wallet.domain.Wallet;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static akka.Done.done;

// tag::wallet[]
@ComponentId("wallet")
public class WalletEntity extends KeyValueEntity<Wallet> {

  // end::wallet[]
  private static final Logger logger = LoggerFactory.getLogger(WalletEntity.class);

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
    return effects().updateState(new Wallet(commandContext().entityId(), initialBalance)).thenReply(done());
  }

  public Effect<WalletResult> withdraw(int amount) { // <2>
    if (currentState().balance() < amount) {
      return effects().reply(new Failure("Insufficient balance"));
    } else {
      Wallet updateWallet = currentState().withdraw(amount);
      // end::wallet[]
      logger.info("Withdraw walletId: [{}] amount -{} balance after {}", currentState().id(), amount, updateWallet.balance());
      // tag::wallet[]
      return effects().updateState(updateWallet).thenReply(new Success());
    }
  }

  public Effect<WalletResult> deposit(int amount) { // <3>
    if (currentState() == null) {
      return effects().reply(new Failure("Wallet [" + commandContext().entityId() + "] not exists"));
    } else {
      Wallet updateWallet = currentState().deposit(amount);
      // end::wallet[]
      logger.info("Deposit walletId: [{}] amount +{} balance after {}", currentState().id(), amount, updateWallet.balance());
      // tag::wallet[]
      return effects().updateState(updateWallet).thenReply(new Success());
    }
  }

  public Effect<Integer> get() { // <4>
    return effects().reply(currentState().balance());
  }
}
// end::wallet[]
