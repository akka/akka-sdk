package com.example.application;

import io.prometheus.metrics.core.metrics.Counter;

// tag::metrics[]
public class ShoppingCartMetrics {

  private static final Counter createdShoppingCarts = Counter.builder() // <1>
    .name("shopping_cart_created_counter")
    .help("Total shopping cart created")
    .register();

  public static void shoppingCartCreated() { // <2>
    createdShoppingCarts.inc();
  }
}
// end::metrics[]
