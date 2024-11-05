/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("wallet")
public class WalletEntity extends KeyValueEntity<Wallet> {

  private static final Logger logger = LoggerFactory.getLogger(WalletEntity.class);

  public Effect<String> create(int amount) {
    return effects().updateState(new Wallet(commandContext().entityId(), amount)).thenReply("Ok");
  }

  public Effect<String> withdraw(int amount) {
    logger.info("Withdraw from {} amount -{}", currentState().id, amount);
    if (amount > currentState().balance) {
      return effects().error("not sufficient funds");
    } else {
      return effects().updateState(currentState().withdraw(amount)).thenReply("ok");
    }
  }

  public Effect<String> deposit(int amount) {
    logger.info("Deposit from {} amount +{}", currentState().id, amount);
    return effects().updateState(currentState().deposit(amount)).thenReply("Ok");
  }

  public Effect<Balance> get() {
    return effects().reply(new Balance(currentState().balance));
  }
}
