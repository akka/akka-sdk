package com.example.callanotherservice;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.grpc.GrpcClientProvider;
import com.example.grpc.DelegatingGrpcEndpoint;
import com.example.grpc.ExampleGrpcEndpointClient;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;

import java.util.concurrent.CompletionStage;

// tag::delegating-endpoint[]
@GrpcEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class DelegatingGrpcEndpointImpl implements DelegatingGrpcEndpoint {

  private final ExampleGrpcEndpointClient akkaService;

  public DelegatingGrpcEndpointImpl(GrpcClientProvider clientProvider) { // <1>
    akkaService = clientProvider.grpcClientFor(ExampleGrpcEndpointClient.class, "doc-snippets"); // <2>
  }

  @Override
  public CompletionStage<HelloReply> callAkkaService(HelloRequest in) {
    return akkaService.sayHello(in); // <3>
  }

}
// end::delegating-endpoint[]
