package com.example.application;

import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

// tag::metrics[]
public class ShoppingCartMetrics {

  private final Meter meter;
  private final LongCounter shoppingCartCreated;

  public ShoppingCartMetrics(Meter meter) { // <1>
    this.meter = meter;
    this.shoppingCartCreated = meter.counterBuilder("shopping.cart.created") // <2>
      .setDescription("How many shopping carts have been created")
      .setUnit("{created}")
      .build();
  }

  public void shoppingCartCreated() { // <3>
    shoppingCartCreated.add(1);
  }
}
// end::metrics[]
