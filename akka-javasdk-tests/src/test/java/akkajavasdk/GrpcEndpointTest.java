/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.grpc.GrpcClientSettings;
import akka.grpc.GrpcServiceException;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.protocol.TestGrpcServiceClient;
import akkajavasdk.protocol.TestGrpcServiceClientPowerApi;
import akkajavasdk.protocol.TestGrpcServiceOuterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@ExtendWith(Junit5LogCapturing.class)
public class GrpcEndpointTest extends TestKitSupport {

  private TestGrpcServiceClient createGrpcClient() {
    return TestGrpcServiceClient.create(
        GrpcClientSettings.connectToServiceAt("localhost", testKit.getPort(),testKit.getActorSystem())
            .withTls(false),
        testKit.getActorSystem());
  }

  @Test
  public void shouldProvideBasicGrpcEndpoint() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

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
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);
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
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);
    try {

      var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
      var response = await(testClient.delegateToAkkaService(request));

      assertThat(response.getData()).isEqualTo(request.getData());
    } finally {
      testClient.close();
    }
  }

  @Test
  public void shouldAllowGrpcCallFromInternet() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);
    try {

      var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
      var response = await(testClient.aclPublicMethod(request));

      assertThat(response.getData()).isEqualTo(request.getData());
    } finally {
      testClient.close();
    }
  }

  @Test
  public void shouldDenyGrpcCallFromInternetWithCustomCode() {
    var testClient = createGrpcClient(); // FIXME creating this with testkit needs the capability to override ACL headers
    try {
      var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
      await(testClient.aclPrivateMethod(request));
      fail("Expected exception");
    } catch(GrpcServiceException e) {
      assertThat(e.getMessage()).contains("UNAVAILABLE");
    } finally {
      testClient.close();
    }
  }

  @Test
  public void shouldAllowGrpcCallFromOtherService() {
    // FIXME update to avoid casting when we give better access to send headers
    var testClient = (TestGrpcServiceClientPowerApi) createGrpcClient(); // FIXME creating this with testkit needs the capability to override ACL headers

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = await(testClient.aclServiceMethod()
        .addHeader("impersonate-service", "other-service")
        .invoke(request));
    assertThat(response.getData()).isEqualTo(request.getData());

    // should still fail when called from internet since it should override component level ACL
    try {
      await(testClient.aclServiceMethod()
          .invoke(request));
      fail("Expected exception");
    } catch(GrpcServiceException e) {
      assertThat(e.getMessage()).contains("PERMISSION_DENIED");
    }
  }

}
