package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.example.application.ShoppingCartMetrics;
import io.opentelemetry.api.metrics.Meter;

// tag::metrics[]
@Setup
public class Bootstrap implements ServiceSetup {

  private final Meter meter;

  public Bootstrap(Meter meter) {
    this.meter = meter;
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return DependencyProvider.single(new ShoppingCartMetrics(meter)); // <1>
  }
}
// end::metrics[]
