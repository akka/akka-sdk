/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Wallet {
  public final String id;
  public final int balance;

  @JsonCreator
  public Wallet(@JsonProperty("id") String id, @JsonProperty("balance") int balance) {
    this.id = id;
    this.balance = balance;
  }

  public Wallet withdraw(int amount) {
    return new Wallet(id, balance - amount);
  }

  public Wallet deposit(int amount) {
    return new Wallet(id, balance + amount);
  }
}
