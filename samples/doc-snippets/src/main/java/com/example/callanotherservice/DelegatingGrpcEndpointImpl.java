package com.example.callanotherservice;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.grpc.GrpcClientProvider;
import com.example.proto.DelegatingGrpcEndpoint;
import com.example.proto.ExampleGrpcEndpointClient;
import com.example.proto.HelloReply;
import com.example.proto.HelloRequest;

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
  public HelloReply callAkkaService(HelloRequest in) {
    return akkaService.sayHello(in); // <3>
  }

}
// end::delegating-endpoint[]
