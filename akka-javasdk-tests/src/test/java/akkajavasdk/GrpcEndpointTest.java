/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import akka.grpc.GrpcServiceException;
import akka.grpc.javadsl.SingleBlockingResponseRequestBuilder;
import akka.javasdk.Principal;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.protocol.TestGrpcServiceClient;
import akkajavasdk.protocol.TestGrpcServiceOuterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class GrpcEndpointTest extends TestKitSupport {

  @Test
  public void shouldProvideBasicGrpcEndpoint() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = testClient.simple(request);

    assertThat(response.getData()).isEqualTo(request.getData());
    assertThat(response.getWasOnVirtualThread()).isTrue();
  }

  @Test
  public void shouldProvideAccessToRequestMetadata() {
    var testClient =
        getGrpcEndpointClient(TestGrpcServiceClient.class).addRequestHeader("x-foo", "bar");

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("x-foo").build();
    var response = testClient.readMetadata(request);

    assertThat(response.getData()).isEqualTo("bar");
  }

  @Test
  public void shouldAllowExternalGrpcCall() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = testClient.delegateToExternal(request);

    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldAllowCrossServiceGrpcCall() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = testClient.delegateToAkkaService(request);

    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldPropagateCallerSpiffeOnCrossServiceGrpcCall() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

    // the endpoint makes an outbound Akka-service gRPC call ("other-service"); the downstream
    // reports the caller SPIFFE id it observed, which must be the calling component's full id
    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("other-service").build();
    var response = testClient.delegateEchoCallerSpiffe(request);

    // the service segment carries a dynamic "-IT-<timestamp>" suffix added by the testkit
    assertThat(response.getData())
        .matches(
            "spiffe://akka\\.local/projects/local-dev-mode/svc/sdk-tests[^/]*/grpc-endpoint/akkajavasdk\\.TestGrpcService");
  }

  @Test
  public void shouldNotLeakCallerSpiffeOnExternalGrpcCall() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

    // the endpoint makes an outbound call to an external service ("some.example.com"); the internal
    // caller SPIFFE header must not be sent to external services
    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("some.example.com").build();
    var response = testClient.delegateEchoCallerSpiffe(request);

    assertThat(response.getData()).isEmpty();
  }

  @Test
  public void shouldPropagateCustomStatusToClient() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);
    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("error").build();
    // when the service throws a gRPC status exception
    try {
      testClient.customStatus(request);
      fail("Expected exception");
    } catch (GrpcServiceException e) {
      assertThat(e.getMessage()).contains("INVALID_ARGUMENT");
    }

    // when the service throws an IllegalArgumentException
    try {
      request = TestGrpcServiceOuterClass.In.newBuilder().setData("illegal").build();
      testClient.customStatus(request);
      fail("Expected exception");
    } catch (GrpcServiceException e) {
      assertThat(e.getMessage()).contains("INVALID_ARGUMENT");
    }

    // when the service throws a RuntimeException
    try {
      request = TestGrpcServiceOuterClass.In.newBuilder().setData("error-dev-details").build();
      testClient.customStatus(request);
      fail("Expected exception");
    } catch (GrpcServiceException e) {
      // making sure that we are logging a correlation ID in the error message
      assertThat(e.getMessage()).contains("INTERNAL: Unexpected error [");
      assertThat(e.getMessage()).contains("All the details in dev mode");
    }
  }

  @Test
  public void shouldAllowGrpcCallFromInternet() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class);

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = testClient.aclPublic(request);

    assertThat(response.getData()).isEqualTo(request.getData());
  }

  @Test
  public void shouldAllowGrpcCallFromOtherService() {
    var clientFromOtherService =
        getGrpcEndpointClient(TestGrpcServiceClient.class, Principal.localService("other-service"));

    var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
    var response = clientFromOtherService.aclService(request);
    assertThat(response.getData()).isEqualTo(request.getData());

    // should still fail when called from internet since it should override component level ACL
    var clientFromInternet = getGrpcEndpointClient(TestGrpcServiceClient.class, Principal.INTERNET);
    expectFailWith(clientFromInternet.aclService(), "PERMISSION_DENIED");
  }

  @Test
  public void shouldInheritDenyCode() {
    var testClient = getGrpcEndpointClient(TestGrpcServiceClient.class, Principal.INTERNET);

    // should inherit deny code defined at class level
    expectFailWith(
        testClient.aclInheritedDenyCode().addHeader("impersonate-service", "other-service"),
        "NOT_FOUND");

    // should override deny code defined at class level
    expectFailWith(testClient.aclOverrideDenyCode(), "UNAVAILABLE");

    // should default to FORBIDDEN if not defined in method's @ACL anno
    expectFailWith(testClient.aclDefaultDenyCode(), "PERMISSION_DENIED");
  }

  private void expectFailWith(
      SingleBlockingResponseRequestBuilder<
              TestGrpcServiceOuterClass.In, TestGrpcServiceOuterClass.Out>
          method,
      String expected) {
    try {
      var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
      method.invoke(request);
      fail("Expected exception");
    } catch (GrpcServiceException e) {
      assertThat(e.getMessage()).contains(expected);
    }
  }
}
