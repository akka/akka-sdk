/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.grpc.GrpcClientSettings;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.protocol.TestGrpcServiceClient;
import akkajavasdk.protocol.TestGrpcServiceOuterClass;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(Junit5LogCapturing.class)
public class GrpcEndpointTest extends TestKitSupport {

  @Test
  public void shouldProvideBasicGrpcEndpoint() {
    var testClient = TestGrpcServiceClient.create(
        GrpcClientSettings.connectToServiceAt("localhost", testKit.getPort(),testKit.getActorSystem())
            .withTls(false),
        testKit.getActorSystem());

    try {
      var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
      var response = await(testClient.simple(request));

      assertThat(response.getData()).isEqualTo(request.getData());
    } finally {
      testClient.close();
    }
  }

  @Test
  public void shouldAllowExternalGrpcCall() {
    var testClient = TestGrpcServiceClient.create(
        GrpcClientSettings.connectToServiceAt("localhost", testKit.getPort(),testKit.getActorSystem())
            .withTls(false),
        testKit.getActorSystem());
    try {

      var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
      var response = await(testClient.delegateToExternal(request));

      assertThat(response.getData()).isEqualTo(request.getData());
    } finally {
      testClient.close();
    }
  }

  @Test
  public void shouldAllowCrossServiceGrpcCall() {
    var testClient = TestGrpcServiceClient.create(
        GrpcClientSettings.connectToServiceAt("localhost", testKit.getPort(),testKit.getActorSystem())
            .withTls(false),
        testKit.getActorSystem());
    try {

      var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
      var response = await(testClient.delegateToAkkaService(request));

      assertThat(response.getData()).isEqualTo(request.getData());
    } finally {
      testClient.close();
    }
  }



}
