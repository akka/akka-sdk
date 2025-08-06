package com.example.transfer;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.example.transfer.application.FraudDetectionService;
import com.example.transfer.application.WalletService;

@Setup
public class Bootstrap implements ServiceSetup {

  @Override
  public DependencyProvider createDependencyProvider() {
    var walletService = new WalletService();
    walletService.deposit("a", 10000);
    walletService.deposit("b", 10000);
    var fraudDetectionService = new FraudDetectionService();

    return new DependencyProvider() {
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == WalletService.class) {
          return (T) walletService;
        } else if (clazz == FraudDetectionService.class) {
          return (T) fraudDetectionService;
        }
        throw new IllegalArgumentException("Dependency not found: " + clazz.getName());
      }
    };
  }
}
