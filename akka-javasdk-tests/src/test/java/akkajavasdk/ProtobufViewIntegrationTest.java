/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity.ChangeNameCommand;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity.CreateCustomerCommand;
import akkajavasdk.components.views.protobuf.ProtobufCustomersByNameView;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration test for a view that subscribes to protobuf events from an event sourced entity using
 * a single GeneratedMessageV3 handler.
 */
@ExtendWith(Junit5LogCapturing.class)
public class ProtobufViewIntegrationTest extends TestKitSupport {

  @Test
  public void shouldUpdateViewFromProtobufEvents() {
    var customerId = "view-proto-test-1";
    var client = componentClient.forEventSourcedEntity(customerId);

    // Create customer
    client
        .method(ProtobufCustomerEntity::create)
        .invoke(new CreateCustomerCommand("Alice Proto", "alice@proto.com"));

    // Wait for view to be updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var result =
                  componentClient
                      .forView()
                      .method(ProtobufCustomersByNameView::getCustomerByName)
                      .invoke(new ProtobufCustomersByNameView.QueryByName("Alice Proto"));
              assertThat(result).isPresent();
              assertThat(result.get().customerId()).isEqualTo(customerId);
              assertThat(result.get().email()).isEqualTo("alice@proto.com");
            });
  }

  @Test
  public void shouldUpdateViewAfterNameChange() {
    var customerId = "view-proto-test-2";
    var client = componentClient.forEventSourcedEntity(customerId);

    // Create customer
    client
        .method(ProtobufCustomerEntity::create)
        .invoke(new CreateCustomerCommand("Bob Proto", "bob@proto.com"));

    // Wait for initial view entry
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var result =
                  componentClient
                      .forView()
                      .method(ProtobufCustomersByNameView::getCustomerByName)
                      .invoke(new ProtobufCustomersByNameView.QueryByName("Bob Proto"));
              assertThat(result).isPresent();
            });

    // Change name
    client.method(ProtobufCustomerEntity::changeName).invoke(new ChangeNameCommand("Bob Updated"));

    // Wait for view to reflect the name change
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var result =
                  componentClient
                      .forView()
                      .method(ProtobufCustomersByNameView::getCustomerByName)
                      .invoke(new ProtobufCustomersByNameView.QueryByName("Bob Updated"));
              assertThat(result).isPresent();
              assertThat(result.get().customerId()).isEqualTo(customerId);
              assertThat(result.get().email()).isEqualTo("bob@proto.com");
            });
  }
}
