package com.example.api;

import akka.javasdk.annotations.GrpcEndpoint;
import customer.api.proto.Customer;
import customer.api.proto.CustomerGrpcEndpoint;
import customer.api.proto.GetCustomerRequest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@GrpcEndpoint // <1>
public class CustomerGrpcEndpointImpl implements CustomerGrpcEndpoint {

  @Override
  public CompletionStage<Customer> getCustomer(GetCustomerRequest in) {
    // dummy implementation with hardcoded values
    var customer = Customer.newBuilder() // <2>
        .setName("Alice")
        .setEmail("alice@email.com")
        .build();
    return CompletableFuture.completedFuture(customer); // <3>
  }
}
