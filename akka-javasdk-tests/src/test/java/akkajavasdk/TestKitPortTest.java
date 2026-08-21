/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import akka.actor.ExtendedActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.testkit.TestKit;
import akkajavasdk.protocol.TestGrpcServiceClient;
import akkajavasdk.protocol.TestGrpcServiceOuterClass;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kalix.runtime.Serve;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The testkit binds the port it was configured with, or fails saying why. Every other test class in
 * this JVM binds the same configured port in turn, so these tests start their own testkits rather
 * than extending TestKitSupport, and wait for the port to be free before starting.
 */
@ExtendWith(Junit5LogCapturing.class)
public class TestKitPortTest {

  private static final String INTERFACE = "127.0.0.1";

  private static final int CONFIGURED_PORT =
      ConfigFactory.load().getInt("akka.javasdk.testkit.http-port");

  @Test
  public void shouldUseTheConfiguredPort() {
    awaitPortFree(CONFIGURED_PORT);
    TestKit testKit = new TestKit(TestKit.Settings.DEFAULT).start();
    try {
      assertThat(testKit.getPort()).isEqualTo(CONFIGURED_PORT);
      // and the port it reports is the one serving
      assertThat(statusOf(testKit, "/query/one?a=a&b=1&c=-1")).isEqualTo(200);
    } finally {
      testKit.stop();
    }
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  public void shouldFailWhenTheConfiguredPortIsTaken() throws IOException {
    awaitPortFree(CONFIGURED_PORT);
    try (ServerSocket squatter = boundSocket(CONFIGURED_PORT)) {
      assertThat(squatter.isBound()).isTrue();

      TestKit testKit = new TestKit(TestKit.Settings.DEFAULT);
      Throwable thrown = catchThrowable(testKit::start);

      // it fails rather than blocking on a port it never got
      assertThat(thrown).isNotNull();
      String reason = allMessages(thrown);
      assertThat(reason).contains(INTERFACE).contains(String.valueOf(CONFIGURED_PORT));
      // and the reason is the runtime's own bind failure, not a timeout waiting for one
      assertThat(causeChain(thrown)).hasAtLeastOneElementOfType(BindException.class);
    }
  }

  @Test
  public void shouldAssignAnEphemeralPortWhenAskedFor() {
    TestKit testKit = new TestKit(TestKit.Settings.DEFAULT.withEphemeralPort()).start();
    try {
      assertAssignedPort(testKit);
      System.out.println("Ephemeral testkit port: " + testKit.getPort());

      // requests reach the assigned port
      assertThat(statusOf(testKit, "/query/one?a=a&b=1&c=-1")).isEqualTo(200);

      // and the gRPC client entry the testkit writes carries the assigned port. The entries written
      // by hand in application.conf cannot, so delegateToAkkaService and delegateToExternal are not
      // called here.
      var client = testKit.getGrpcEndpointClient(TestGrpcServiceClient.class);
      var request = TestGrpcServiceOuterClass.In.newBuilder().setData("Hello world").build();
      assertThat(client.simple(request).getData()).isEqualTo("Hello world");
    } finally {
      testKit.stop();
    }
  }

  @Test
  public void shouldTreatAConfiguredZeroAsAskingForOne() {
    TestKit testKit =
        new TestKit(
                TestKit.Settings.DEFAULT.withAdditionalConfig("akka.javasdk.testkit.http-port = 0"))
            .start();
    try {
      assertAssignedPort(testKit);
      assertThat(statusOf(testKit, "/query/one?a=a&b=1&c=-1")).isEqualTo(200);
    } finally {
      testKit.stop();
    }
  }

  private static void assertAssignedPort(TestKit testKit) {
    assertThat(testKit.getPort()).isNotZero();
    assertThat(testKit.getPort()).isNotEqualTo(CONFIGURED_PORT);
    // the runtime reads a dev mode port of 0 as "not configured" and falls back to a fixed port,
    // which would collide the same way an assigned port is asked for to avoid
    assertThat(testKit.getPort())
        .isNotEqualTo(ConfigFactory.defaultReference().getInt("akka.runtime.http-port"));
  }

  @Test
  public void shouldRejectAnotherRuntimesUid() {
    awaitPortFree(CONFIGURED_PORT);
    TestKit testKit = new TestKit(TestKit.Settings.DEFAULT).start();
    try {
      long selfUid = ((ExtendedActorSystem) testKit.getActorSystem().classicSystem()).uid();

      // this runtime confirms its own uid
      assertThat(statusOf(testKit, Serve.DevModeStartedPath() + "?uid=" + selfUid)).isEqualTo(200);

      // and does not confirm another runtime's
      assertThat(statusOf(testKit, Serve.DevModeStartedPath() + "?uid=" + (selfUid + 1)))
          .isNotEqualTo(200);
    } finally {
      testKit.stop();
    }
  }

  private static int statusOf(TestKit testKit, String path) {
    String url = "http://" + INTERFACE + ":" + testKit.getPort() + path;
    HttpResponse response =
        Http.get(testKit.getActorSystem())
            .singleRequest(HttpRequest.GET(url))
            .toCompletableFuture()
            .join();
    response.discardEntityBytes(testKit.getMaterializer());
    return response.status().intValue();
  }

  private static ServerSocket boundSocket(int port) throws IOException {
    ServerSocket socket = new ServerSocket();
    socket.setReuseAddress(true);
    socket.bind(new InetSocketAddress(INTERFACE, port));
    return socket;
  }

  /** Other test classes in this JVM hold the same port, so wait for the previous one to let go. */
  private static void awaitPortFree(int port) {
    Awaitility.await("port " + port + " to be free")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> isFree(port));
  }

  private static boolean isFree(int port) {
    try (ServerSocket ignored = boundSocket(port)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static List<Throwable> causeChain(Throwable thrown) {
    List<Throwable> chain = new ArrayList<>();
    for (Throwable t = thrown; t != null && !chain.contains(t); t = t.getCause()) {
      chain.add(t);
    }
    return chain;
  }

  private static String allMessages(Throwable thrown) {
    StringBuilder messages = new StringBuilder();
    for (Throwable t : causeChain(thrown)) {
      messages.append(t).append('\n');
    }
    return messages.toString();
  }
}
