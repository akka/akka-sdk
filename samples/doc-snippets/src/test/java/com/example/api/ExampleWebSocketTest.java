package com.example.api;

import akka.javasdk.testkit.TestKitSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class ExampleWebSocketTest extends TestKitSupport {

  // @Test - no such endpoint in the snippets, just here for for docs
  void shouldSupportTextWebSockets() {
    // tag::ws-testkit[]
    var webSocketRouteTester = testKit.getSelfWebSocketRouteTester(); // <1>

    var probes = webSocketRouteTester.wsTextConnection("/ping-pong-websocket"); // <2>

    var publisher = probes.publisher();
    var subscriber = probes.subscriber();

    subscriber.request(1); // <3>

    publisher.sendNext("ping"); // <4>

    var messageBack = subscriber.expectNext(); // <5>
    assertThat(messageBack).isEqualTo("pong");

    publisher.sendComplete(); // <6>
    subscriber.expectComplete();
    // end::ws-testkit[]
  }
}

