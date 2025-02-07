/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.grpc.GrpcServiceException;
import akka.grpc.javadsl.SingleResponseRequestBuilder;
import akka.javasdk.Principal;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.protocol.TestGrpcServiceClient;
import akkajavasdk.protocol.TestGrpcServiceOuterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@ExtendWith(Junit5LogCapturing.class)
public class GrpcEndpointTest extends TestKitSupport {

  @Test
  public void shouldProvideBasicGrpcEndpoint() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = await(testClient.simple(request));

    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldAllowExternalGrpcCall() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = await(testClient.delegateToExternal(request));

    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldAllowCrossServiceGrpcCall() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = await(testClient.delegateToAkkaService(request));

    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldAllowGrpcCallFromInternet() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = await(testClient.aclPublic(request));

    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldAllowGrpcCallFromOtherService() {
    var clientFromOtherService = getGrpcEndpointClient(TestGrpcServiceClient.class, Principal.localService("other-service"));

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = await(clientFromOtherService.aclService(request));
    assertThat(response.getData()).isEqualTo(request.getData());

    // should still fail when called from internet since it should override component level ACL
    var clientFromInternet = getGrpcEndpointClient(TestGrpcServiceClient.class, Principal.INTERNET);
    expectFailWith(clientFromInternet.aclService(), "PERMISSION_DENIED");
  }

  @Test
  public void shouldInheritDenyCode() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class, Principal.INTERNET);

    // should inherit deny code defined at class level
    expectFailWith(testClient.aclInheritedDenyCode()
        .addHeader("impersonate-service", "other-service"), "NOT_FOUND");

    // should override deny code defined at class level
    expectFailWith(testClient.aclOverrideDenyCode(), "UNAVAILABLE");

    // should default to FORBIDDEN if not defined in method's @ACL anno
    expectFailWith(testClient.aclDefaultDenyCode(), "PERMISSION_DENIED");
  }

  private void expectFailWith(SingleResponseRequestBuilder<TestGrpcServiceOuterClass.In, TestGrpcServiceOuterClass.Out> method, String expected) {
    try {
      var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
      await(method
          .invoke(request));
      fail("Expected exception");
    } catch (GrpcServiceException e) {
      assertThat(e.getMessage()).contains(expected);
    }

  }

}
