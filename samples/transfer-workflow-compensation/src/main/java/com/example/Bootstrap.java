package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.example.transfer.application.FraudDetectionService;

@Setup
public class Bootstrap implements ServiceSetup {

  @Override
  public DependencyProvider createDependencyProvider() {
    return DependencyProvider.single(new FraudDetectionService());
  }
}
