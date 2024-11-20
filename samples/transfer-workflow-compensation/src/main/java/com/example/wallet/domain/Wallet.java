package com.example.wallet.domain;

import com.example.wallet.application.WalletEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public record Wallet(String id, int balance, List<String> commandIds) {

  private static final Logger logger = LoggerFactory.getLogger(WalletEntity.class);
  public static Wallet EMPTY = new Wallet("", 0, new ArrayList<>());

  public boolean isEmpty(){
    return id.equals("");
  }

  public List<WalletEvent> handle(WalletCommand command) {
    // a very basic dedupliction mechanism, make sure to put some constraints on the commandIds list size
    // for instance based on the number of commands you expect to be processed
    // and based on the time window you expect to process them in
    if (commandIds.contains(command.commandId())) {
      logger.info("Command already processed: [{}]", command.commandId());
      return List.of();
    }
    return switch (command) {
      case WalletCommand.Deposit deposit ->
        List.of(new WalletEvent.Deposited(command.commandId(), deposit.amount()));
      case WalletCommand.Withdraw withdraw ->
        List.of(new WalletEvent.Withdrawn(command.commandId(), withdraw.amount()));
    };
  }


  public Wallet applyEvent(WalletEvent event) {
    return switch (event) {
      case WalletEvent.Created created ->
        new Wallet(created.walletId(), created.initialBalance(), new ArrayList<>());
      case WalletEvent.Withdrawn withdrawn -> {
        commandIds.add(withdrawn.commandId());
        yield new Wallet(id, balance - withdrawn.amount(), commandIds);
      }
      case WalletEvent.Deposited deposited -> {
        commandIds.add(deposited.commandId());
        yield new Wallet(id, balance + deposited.amount(), commandIds);
      }
    };
  }
}