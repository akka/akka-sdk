package com.example.callanotherservice;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.grpc.GrpcClientProvider;
import com.example.grpc.CallExternalGrpcEndpoint;
import com.example.grpc.ExampleGrpcEndpointClient;
import com.example.grpc.HelloReply;
import com.example.grpc.HelloRequest;

import java.util.concurrent.CompletionStage;

// tag::call-external-endpoint[]
@GrpcEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class CallExternalGrpcEndpointImpl implements CallExternalGrpcEndpoint {
  private final ExampleGrpcEndpointClient external;

  public CallExternalGrpcEndpointImpl(GrpcClientProvider clientProvider) { // <1>
    external = clientProvider.grpcClientFor(ExampleGrpcEndpointClient.class, "hellogrpc.example.com"); // <2>
  }

  @Override
  public CompletionStage<HelloReply> callExternalService(HelloRequest in) {
    return external.sayHello(in); // <3>
  }
}
// end::call-external-endpoint[]
