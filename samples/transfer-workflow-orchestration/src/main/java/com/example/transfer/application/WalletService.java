package com.example.transfer.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dummy wallet service for managing wallet balances.
 * The real implementation could involve external service calls.
 */
public class WalletService {

  private static final Logger log = LoggerFactory.getLogger(WalletService.class);

  private Map<String, Integer> walletBalances = new ConcurrentHashMap<>();

  public void withdraw(String fromWalletId, int amount) {
    //TODO: implement your withdrawal logic here
    walletBalances.compute(fromWalletId, (k, v) -> v - amount);
    log.info("Withdrawn from: {} amount: {}", fromWalletId, amount);
  }

  public void deposit(String toWalletId, int amount) {
    //TODO: implement your deposit logic here
    walletBalances.compute(toWalletId, (k, v) -> v == null ? amount : v + amount);
    log.info("Deposited to: {} amount: {}", toWalletId, amount);
  }

  public int getBalance(String walletId) {
    return walletBalances.get(walletId);
  }
}
