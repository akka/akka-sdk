/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKit;
import akkajavasdk.protocol.TestGrpcServiceClient;
import akkajavasdk.protocol.TestGrpcServiceOuterClass;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MockedServicesTest {

  static final class MockedGrpcService extends TestGrpcServiceClient {
    final ConcurrentLinkedQueue<String> seenData = new ConcurrentLinkedQueue<>();

    @Override
    public TestGrpcServiceOuterClass.Out simple(TestGrpcServiceOuterClass.In in) {
      seenData.add(in.getData());
      return TestGrpcServiceOuterClass.Out.newBuilder()
          .setData("mocked:" + in.getData())
          .setWasOnVirtualThread(false)
          .build();
    }

    @Override
    public TestGrpcServiceOuterClass.Out readMetadata(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TestGrpcServiceOuterClass.Out delegateToAkkaService(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TestGrpcServiceOuterClass.Out delegateToExternal(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TestGrpcServiceOuterClass.Out echoCallerSpiffe(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TestGrpcServiceOuterClass.Out delegateEchoCallerSpiffe(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TestGrpcServiceOuterClass.Out customStatus(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TestGrpcServiceOuterClass.Out aclPublic(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TestGrpcServiceOuterClass.Out aclService(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TestGrpcServiceOuterClass.Out aclInheritedDenyCode(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TestGrpcServiceOuterClass.Out aclOverrideDenyCode(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TestGrpcServiceOuterClass.Out aclDefaultDenyCode(TestGrpcServiceOuterClass.In in) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<akka.Done> close() {
      return java.util.concurrent.CompletableFuture.completedFuture(akka.Done.getInstance());
    }

    @Override
    public CompletionStage<akka.Done> closed() {
      return new java.util.concurrent.CompletableFuture<>();
    }
  }

  final ConcurrentLinkedQueue<String> billingRequestUris = new ConcurrentLinkedQueue<>();
  final MockedGrpcService grpcMock = new MockedGrpcService();

  private TestKit testKit;

  @BeforeAll
  public void beforeAll() {
    testKit =
        new TestKit(
                TestKit.Settings.DEFAULT
                    .withMockedHttpService(
                        "billing",
                        request -> {
                          billingRequestUris.add(request.getUri().toString());
                          return HttpResponse.create()
                              .withStatus(StatusCodes.OK)
                              .withEntity(ContentTypes.APPLICATION_JSON, "{\"billed\":true}");
                        })
                    .withMockedHttpService(
                        "accounts",
                        request ->
                            HttpResponse.create()
                                .withStatus(StatusCodes.OK)
                                .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "accounts-mock"))
                    .withMockedGrpcService("billing-grpc", TestGrpcServiceClient.class, grpcMock))
            .start();
  }

  @AfterAll
  public void afterAll() {
    if (testKit != null) testKit.stop();
  }

  @AfterEach
  public void resetMocks() {
    testKit.getMockedHttpServices().reset();
    testKit.getMockedGrpcServices().reset();
  }

  @Test
  public void httpMockReturnsMockedResponse() {
    var response =
        testKit.getHttpClientProvider().httpClientFor("billing").GET("/invoice/42").invoke();

    assertThat(response.httpResponse().status().intValue()).isEqualTo(200);
    assertThat(response.body().utf8String()).isEqualTo("{\"billed\":true}");
    assertThat(billingRequestUris).contains("http://billing/invoice/42");
  }

  @Test
  public void multipleHttpMocksAreIndependent() {
    var billingResponse =
        testKit.getHttpClientProvider().httpClientFor("billing").POST("/charge").invoke();
    var accountsResponse =
        testKit.getHttpClientProvider().httpClientFor("accounts").GET("/me").invoke();

    assertThat(billingResponse.body().utf8String()).isEqualTo("{\"billed\":true}");
    assertThat(accountsResponse.body().utf8String()).isEqualTo("accounts-mock");
  }

  @Test
  public void grpcMockReturnsMockedResponse() {
    var client =
        testKit.getGrpcClientProvider().grpcClientFor(TestGrpcServiceClient.class, "billing-grpc");
    var response =
        client.simple(TestGrpcServiceOuterClass.In.newBuilder().setData("invoice-42").build());

    assertThat(response.getData()).isEqualTo("mocked:invoice-42");
    assertThat(grpcMock.seenData).contains("invoice-42");
  }

  @Test
  public void mockResponseCanBeReplacedPerTest() {
    testKit
        .getMockedHttpServices()
        .mockResponse(
            "billing",
            request ->
                HttpResponse.create()
                    .withStatus(StatusCodes.NOT_FOUND)
                    .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "replaced"));

    var response =
        testKit.getHttpClientProvider().httpClientFor("billing").GET("/anything").invoke();
    assertThat(response.httpResponse().status()).isEqualTo(StatusCodes.NOT_FOUND);
    assertThat(response.body().utf8String()).isEqualTo("replaced");
  }

  @Test
  public void resetRestoresSettingsDefaults() {
    testKit
        .getMockedHttpServices()
        .mockResponse(
            "billing", request -> HttpResponse.create().withStatus(StatusCodes.NOT_FOUND));
    testKit.getMockedHttpServices().reset();

    var response =
        testKit.getHttpClientProvider().httpClientFor("billing").GET("/after-reset").invoke();
    assertThat(response.body().utf8String()).isEqualTo("{\"billed\":true}");
  }

  @Test
  public void mockCanBeRegisteredPerTestWithoutSettings() {
    testKit
        .getMockedHttpServices()
        .mockResponse(
            "late-bound",
            request ->
                HttpResponse.create()
                    .withStatus(StatusCodes.OK)
                    .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "late"));

    var response = testKit.getHttpClientProvider().httpClientFor("late-bound").GET("/x").invoke();
    assertThat(response.body().utf8String()).isEqualTo("late");
  }

  @Test
  public void grpcMockLookedUpRepeatedlyReturnsSameInstance() {
    var c1 =
        testKit.getGrpcClientProvider().grpcClientFor(TestGrpcServiceClient.class, "billing-grpc");
    var c2 =
        testKit.getGrpcClientProvider().grpcClientFor(TestGrpcServiceClient.class, "billing-grpc");
    assertThat(c1).isSameAs(c2);
  }
}
