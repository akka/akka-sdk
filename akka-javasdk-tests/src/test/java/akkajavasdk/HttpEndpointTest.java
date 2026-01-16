/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.RawHeader;
import akka.javasdk.testkit.TestKitSupport;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.http.ResourcesEndpoint;
import akkajavasdk.components.http.TestEndpoint;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class HttpEndpointTest extends TestKitSupport {

  @Test
  public void shouldGetQueryParams() {
    var response = httpClient.GET("/query/one?a=a&b=1&c=-1").responseBodyAs(String.class).invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body()).isEqualTo("name: one, a: a, b: 1, c: -1");
  }

  @Test
  public void shouldRetryComponentClientCall() {
    var response =
        await(httpClient.POST("/retry/retry1").responseBodyAs(Integer.class).invokeAsync());
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body()).isEqualTo(111);
  }

  @Test
  public void shouldRetryWithAsyncUtils() {
    var response =
        await(httpClient.POST("/async-utils/retry2").responseBodyAs(Integer.class).invokeAsync());
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body()).isEqualTo(111);
  }

  @Test
  public void shouldRetryHttpCall() {
    var response =
        await(
            httpClient
                .POST("/failing/retry3")
                .responseBodyAs(Integer.class)
                .withRetry(3)
                .invokeAsync());
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body()).isEqualTo(111);
  }

  @Test
  public void shouldServeASingleResource() {
    var response = httpClient.GET("/index.html").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
  }

  @Test
  public void endpointShouldRunOnVirtualThread() {
    var response = httpClient.GET("/on-virtual").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
  }

  @Test
  public void resolveSelfServiceNameInIntegrationTest() {
    var response =
        testKit.getHttpClientProvider().httpClientFor("sdk-tests").GET("/index.html").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
  }

  @Test
  public void shouldServeWildcardResources() throws Exception {
    var htmlResponse = httpClient.GET("/static/index.html").invoke();
    assertThat(htmlResponse.status()).isEqualTo(StatusCodes.OK);
    assertThat(htmlResponse.httpResponse().entity().getContentType())
        .isEqualTo(ContentTypes.TEXT_HTML_UTF8);

    try (InputStream in =
        this.getClass().getClassLoader().getResourceAsStream("static-resources/index.html")) {
      var bytes = ByteString.fromArray(in.readAllBytes());
      assertThat(htmlResponse.body()).isEqualTo(bytes);
      assertThat(htmlResponse.httpResponse().entity().getContentLengthOption())
          .isEqualTo(OptionalLong.of(bytes.size()));
    }

    var otherResourcesWithKnownTypes =
        Set.of(
            "/static/script.js",
            "/static/style.css",
            "/static/sample-pdf-file.pdf",
            "/static/images/image.png",
            "/static/images/image.jpg",
            "/static/images/image.gif");

    otherResourcesWithKnownTypes.forEach(
        resourcePath -> {
          var response = httpClient.GET(resourcePath).invoke();
          assertThat(response.httpResponse().entity().getContentType())
              .isNotEqualTo(ContentTypes.APPLICATION_OCTET_STREAM);
        });

    var response = httpClient.GET("/static/unknown-type.zip").invoke();
    assertThat(response.httpResponse().entity().getContentType())
        .isEqualTo(ContentTypes.APPLICATION_OCTET_STREAM);
  }

  @Test
  public void return404ForNonexistentResource() {
    var response = httpClient.GET("/static/does-not-exist").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.NOT_FOUND);
  }

  @Test
  public void shouldNotAllowParentPathReferences() {
    // Akka HTTP client normalizes .. so this can't be exploited through a path
    // like this: http://host:port/static/../akkajavasdk/HttpEndpointTest.class
    // a custom user scheme getting the path from somewhere else than request path
    // like this could let the .. through to the classpath resource util though
    var response =
        httpClient
            .POST("/static-exploit-try")
            .withRequestBody(
                new ResourcesEndpoint.SomeRequest("../akkajavasdk/HttpEndpointTest.class"))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.FORBIDDEN);
  }

  @Test
  public void shouldHandleCollectionsAsBody() {
    var list = List.of(new TestEndpoint.SomeRecord("text", 1));
    var response =
        httpClient
            .POST("/list-body")
            .withRequestBody(list)
            .responseBodyAsListOf(TestEndpoint.SomeRecord.class)
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body()).isEqualTo(list);
  }

  @Test
  public void injectableSanitizerWorks() {
    // depends on custom sanitizer config, see test application.conf
    var response = httpClient.GET("/sanitized").responseBodyAs(String.class).invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body()).isEqualTo("Here's a string to sanitize: ************************");

    var directUsageResult =
        getSanitizer().sanitize("Here's a string to sanitize: sanitizesanitizesanitize");
    assertThat(directUsageResult)
        .isEqualTo("Here's a string to sanitize: ************************");
  }

  @Test
  public void shouldHandleBigDecimalOutOfTheBox() {
    var bigDecimal = new java.math.BigDecimal("12345678901234567890.12345678901234567890");
    var response =
        httpClient
            .POST("/big-decimal")
            .withRequestBody(new TestEndpoint.BigDecimalRequest(bigDecimal))
            .responseBodyAs(TestEndpoint.BigDecimalRequest.class)
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body().value()).isEqualTo(bigDecimal);
  }

  @Test
  public void shouldSupportStreamingText() {
    // Note: no streaming support in the HTTP client abstraction, so we need to do it manually
    var url = "http://" + testKit.getHost() + ":" + testKit.getPort() + "/streamingtext/5";
    var response =
        await(
            Http.get(testKit.getActorSystem())
                .singleRequest(
                    HttpRequest.GET(url).addHeader(RawHeader.create("Accept", "text/event-stream")))
                .toCompletableFuture());

    assertThat(response.entity().getContentType()).isEqualTo(ContentTypes.TEXT_PLAIN_UTF8);
    assertThat(response.entity().isChunked()).isTrue();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);

    var text =
        await(
            response
                .entity()
                .getDataBytes()
                .map(t -> t.utf8String())
                .runWith(Sink.seq(), testKit.getMaterializer()));

    assertThat(text).isEqualTo(Arrays.asList("1", "2", "3", "4", "5"));
  }

  @Test
  public void shouldSupportSseIds() {
    var sseRouteTester = testKit.getSelfSseRouteTester();
    var firstEvents = sseRouteTester.receiveFirstN("/serversentevents", 2, Duration.ofSeconds(5));
    assertThat(firstEvents).hasSize(2);
    assertThat(firstEvents.get(0).getId().get()).isEqualTo("1");
    assertThat(firstEvents.get(1).getId().get()).isEqualTo("2");

    // Note: not stateful, does not depend on the previous call, just together because otherwise
    // related
    var reconnectEvents =
        sseRouteTester.receiveNFromOffset(
            "/serversentevents",
            1,
            "2", // start from id
            Duration.ofSeconds(5));
    assertThat(reconnectEvents).hasSize(1);
    assertThat(reconnectEvents.get(0).getId().get())
        .isEqualTo("3"); // starts from the reported Last-Event-ID
  }

  @Test
  public void shouldSupportResumableSseForViewStreamUpdates() throws InterruptedException {
    var sseRouteTester = testKit.getSelfSseRouteTester();
    componentClient.forEventSourcedEntity("sse-one").method(CounterEntity::increase).invoke(1);

    new Thread(
            () -> {
              // Another write comes in (while the view is streaming), for test coverage, we want it
              // to have a different timestamp, and be picked up by a later view poll
              try {
                Thread.sleep(1000);
                componentClient
                    .forEventSourcedEntity("sse-two")
                    .method(CounterEntity::increase)
                    .invoke(2);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            })
        .start();

    var firstEvents =
        sseRouteTester.receiveFirstN("/serversentevents/sse-counters", 2, Duration.ofSeconds(5));
    assertThat(firstEvents).hasSize(2);
    assertThat(firstEvents.get(0).getData())
        .isEqualTo(
            """
            {"id":"sse-one","latestEvent":"ValueIncreased[value=1]"}\
            """);

    assertThat(firstEvents.get(1).getData())
        .isEqualTo(
            """
            {"id":"sse-two","latestEvent":"ValueIncreased[value=2]"}\
            """);
    var lastIdFirstStream = firstEvents.get(1).getId().get();

    // only sees new updates, there are no new updates
    catchException(
        () -> {
          sseRouteTester.receiveNFromOffset(
              "/serversentevents/sse-counters", 1, lastIdFirstStream, Duration.ofMillis(200));
        });

    // and then another update with yet another timestamp
    Thread.sleep(10);
    componentClient.forEventSourcedEntity("sse-one").method(CounterEntity::increase).invoke(3);

    // only sees new updates, now there is a new update
    var newEvents =
        sseRouteTester.receiveNFromOffset(
            "/serversentevents/sse-counters", 2, lastIdFirstStream, Duration.ofSeconds(5));
    assertThat(newEvents).hasSize(2);

    // Note that we always get a duplicate, because offset is a timestamp, and there could have been
    // multiple
    // entries with the same timestamp, while the previous stream failed after seeing the first
    assertThat(newEvents.get(0).getData())
        .isEqualTo(
            """
            {"id":"sse-two","latestEvent":"ValueIncreased[value=2]"}\
            """);

    // the updated event
    assertThat(newEvents.get(1).getData())
        .isEqualTo(
            """
            {"id":"sse-one","latestEvent":"ValueIncreased[value=3]"}\
            """);

    var firstInstant = Instant.parse(lastIdFirstStream);
    var secondInstant = Instant.parse(newEvents.get(1).getId().get());

    assertThat(firstInstant).isBefore(secondInstant);
  }

  @Test
  void shouldSupportTextWebSockets() {
    var webSocketRouteTester = testKit.getSelfWebSocketRouteTester();

    var probes = webSocketRouteTester.wsTextConnection("/websocket-text");

    var publisher = probes.publisher();
    var subscriber = probes.subscriber();

    subscriber.request(1);

    publisher.expectRequest();
    publisher.sendNext("ping");

    assertThat(subscriber.expectNext()).isEqualTo("ping");

    publisher.sendComplete();
    subscriber.expectComplete();
  }

  @Test
  void shouldSupportBinaryWebSockets() {
    var webSocketRouteTester = testKit.getSelfWebSocketRouteTester();

    // Test also covers protocol and parameters
    var probes = webSocketRouteTester.wsBinaryConnection("/websocket-binary/5", "limiting");

    var publisher = probes.publisher();
    var subscriber = probes.subscriber();
    assertThat(probes.chosenProtocol()).contains("limiting");

    subscriber.request(1);

    publisher.expectRequest();
    publisher.sendNext(ByteString.fromString("123456"));

    assertThat(subscriber.expectNext()).isEqualTo(ByteString.fromString("12345"));

    publisher.sendComplete();
    subscriber.expectComplete();
  }
}
