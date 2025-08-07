package com.example.callanotherservice;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.grpc.GrpcClientProvider;
import com.example.proto.CallExternalGrpcEndpoint;
import com.example.proto.ExampleGrpcEndpointClient;
import com.example.proto.HelloReply;
import com.example.proto.HelloRequest;

// tag::call-external-endpoint[]
@GrpcEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class CallExternalGrpcEndpointImpl implements CallExternalGrpcEndpoint {

  private final ExampleGrpcEndpointClient external;

  public CallExternalGrpcEndpointImpl(GrpcClientProvider clientProvider) { // <1>
    external = clientProvider.grpcClientFor(
      ExampleGrpcEndpointClient.class,
      "hellogrpc.example.com"
    ); // <2>
  }

  @Override
  public HelloReply callExternalService(HelloRequest in) {
    return external.sayHello(in); // <3>
  }
}
// end::call-external-endpoint[]
