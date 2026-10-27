/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity.ChangeNameCommand;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity.CreateCustomerCommand;
import akkajavasdk.components.keyvalueentities.protobuf.ProtobufCustomerKvEntity;
import akkajavasdk.components.views.protobuf.ProtobufCustomerStateView;
import akkajavasdk.components.views.protobuf.ProtobufCustomersByNameView;
import akkajavasdk.protocol.SerializationTestProtos.CustomerStateList;
import akkajavasdk.protocol.SerializationTestProtos.CustomersByCreationTimeQuery;
import akkajavasdk.protocol.SerializationTestProtos.SimpleMessage;
import akkajavasdk.protocol.SerializationTestProtos.Status;
import com.google.protobuf.util.Timestamps;
import java.util.List;
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

  @Test
  public void shouldQueryViewWithProtobufStateAndParams() {
    var entityId = "view-proto-state-1";
    var client = componentClient.forKeyValueEntity(entityId);

    // Create a customer via the KV entity
    client
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new ProtobufCustomerKvEntity.CreateCommand("ProtoState Alice", "alice@state.com"));

    // Query the view using a protobuf parameter, expecting a protobuf return value
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var queryParam = SimpleMessage.newBuilder().setText("ProtoState Alice").build();
              var result =
                  componentClient
                      .forView()
                      .method(ProtobufCustomerStateView::getCustomerByName)
                      .invoke(queryParam);
              assertThat(result.getName()).isEqualTo("ProtoState Alice");
              assertThat(result.getEmail()).isEqualTo("alice@state.com");
              assertThat(result.getCustomerId()).isEqualTo(entityId);
              assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
              // google.protobuf.Timestamp in state survives the JSON round-trip
              assertThat(result.hasCreatedAt()).isTrue();
              assertThat(result.getCreatedAt().getSeconds()).isGreaterThan(0L);
            });
  }

  @Test
  public void shouldStoreProtobufStateWithUnsetOptionalFieldsInView() {
    var entityId = "view-proto-unset-ts-1";
    var message = SimpleMessage.newBuilder().setText("ProtoState Unset Fields").build();

    // this command creates a state without created_at, nickname and review_date
    componentClient
        .forKeyValueEntity(entityId)
        .method(ProtobufCustomerKvEntity::createFromProtobuf)
        .invoke(message);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var result =
                  componentClient
                      .forView()
                      .method(ProtobufCustomerStateView::getCustomerByName)
                      .invoke(message);
              assertThat(result.getCustomerId()).isEqualTo(entityId);
              assertThat(result.hasCreatedAt()).isFalse();
              assertThat(result.hasNickname()).isFalse();
              assertThat(result.hasReviewDate()).isFalse();
            });
  }

  @Test
  public void shouldQueryViewOnPresenceFields() {
    var withFields = "view-proto-presence-full";
    var withoutFields = "view-proto-presence-bare";
    componentClient
        .forKeyValueEntity(withFields)
        .method(ProtobufCustomerKvEntity::createWithPresenceFields)
        .invoke(
            new ProtobufCustomerKvEntity.CreateWithPresenceFieldsCommand(
                "Presence Full", "presence-nick", "presence@example.com"));
    // no optional, nested message or oneof fields set
    componentClient
        .forKeyValueEntity(withoutFields)
        .method(ProtobufCustomerKvEntity::createFromProtobuf)
        .invoke(SimpleMessage.newBuilder().setText("Presence Bare").build());

    // wait until both entities are in the view, so that the "not contains" checks below cannot pass
    // only because an entity is not indexed yet
    for (var name : List.of("Presence Full", "Presence Bare")) {
      Awaitility.await()
          .ignoreExceptions()
          .atMost(20, TimeUnit.of(SECONDS))
          .untilAsserted(
              () -> {
                var result =
                    componentClient
                        .forView()
                        .method(ProtobufCustomerStateView::getCustomerByName)
                        .invoke(SimpleMessage.newBuilder().setText(name).build());
                assertThat(result.getName()).isEqualTo(name);
              });
    }

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var view = componentClient.forView();

              // optional message field
              assertThat(
                      ids(
                          view.method(ProtobufCustomerStateView::getCustomersWithoutReviewDate)
                              .invoke()))
                  .contains(withoutFields)
                  .doesNotContain(withFields);
              assertThat(
                      ids(
                          view.method(ProtobufCustomerStateView::getCustomersWithReviewDate)
                              .invoke()))
                  .contains(withFields)
                  .doesNotContain(withoutFields);

              // optional scalar field
              assertThat(
                      ids(
                          view.method(ProtobufCustomerStateView::getCustomersWithoutNickname)
                              .invoke()))
                  .contains(withoutFields)
                  .doesNotContain(withFields);
              assertThat(
                      ids(
                          view.method(ProtobufCustomerStateView::getCustomersByNickname)
                              .invoke(SimpleMessage.newBuilder().setText("presence-nick").build())))
                  .containsExactly(withFields);

              // message field without the optional keyword
              assertThat(
                      ids(
                          view.method(ProtobufCustomerStateView::getCustomersWithoutContact)
                              .invoke()))
                  .contains(withoutFields)
                  .doesNotContain(withFields);

              // oneof member
              assertThat(
                      ids(
                          view.method(ProtobufCustomerStateView::getCustomersWithoutPreferredEmail)
                              .invoke()))
                  .contains(withoutFields)
                  .doesNotContain(withFields);
              assertThat(
                      ids(
                          view.method(ProtobufCustomerStateView::getCustomersByPreferredEmail)
                              .invoke(
                                  SimpleMessage.newBuilder()
                                      .setText("presence@example.com")
                                      .build())))
                  .containsExactly(withFields);
            });
  }

  private static List<String> ids(CustomerStateList list) {
    return list.getCustomersList().stream().map(c -> c.getCustomerId()).toList();
  }

  @Test
  public void shouldConstructNestedProtobufMessageInQueryResult() {
    var entityId = "view-proto-construct-1";
    var message = SimpleMessage.newBuilder().setText("Construct Alice").build();
    componentClient
        .forKeyValueEntity(entityId)
        .method(ProtobufCustomerKvEntity::createFromProtobuf)
        .invoke(message);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var view = componentClient.forView();

              // SELECT * AS customer
              var customer =
                  view.method(ProtobufCustomerStateView::getCustomerAsNestedMessage)
                      .invoke(message);
              assertThat(customer.getCustomer().getCustomerId()).isEqualTo(entityId);

              // SELECT (name, email) AS contact
              var contact =
                  view.method(ProtobufCustomerStateView::getCustomerContact).invoke(message);
              assertThat(contact.getContact().getName()).isEqualTo("Construct Alice");
              assertThat(contact.getContact().getEmail()).isEqualTo("from-protobuf@test.com");
            });
  }

  @Test
  public void shouldQueryViewWithTimestampParameter() {
    var beforeAll = Timestamps.fromMillis(System.currentTimeMillis() - 1000);

    // Create two customers via the KV entity, with the timestamp set on each state
    componentClient
        .forKeyValueEntity("view-proto-ts-1")
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new ProtobufCustomerKvEntity.CreateCommand("TS Alice", "ts-alice@state.com"));
    componentClient
        .forKeyValueEntity("view-proto-ts-2")
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new ProtobufCustomerKvEntity.CreateCommand("TS Bob", "ts-bob@state.com"));

    // Query view using google.protobuf.Timestamp as the parameter value
    var queryParam = CustomersByCreationTimeQuery.newBuilder().setCreatedAfter(beforeAll).build();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var result =
                  componentClient
                      .forView()
                      .method(ProtobufCustomerStateView::getCustomersCreatedAfter)
                      .invoke(queryParam);
              assertThat(result.getCustomersList())
                  .extracting(c -> c.getName())
                  .contains("TS Alice", "TS Bob");
              assertThat(result.getCustomersList())
                  .allSatisfy(c -> assertThat(c.hasCreatedAt()).isTrue());
            });

    // A query with a timestamp in the future should return no matches
    var future = Timestamps.fromMillis(System.currentTimeMillis() + 3600_000);
    var futureQuery = CustomersByCreationTimeQuery.newBuilder().setCreatedAfter(future).build();
    var emptyResult =
        componentClient
            .forView()
            .method(ProtobufCustomerStateView::getCustomersCreatedAfter)
            .invoke(futureQuery);
    assertThat(emptyResult.getCustomersList())
        .extracting(c -> c.getName())
        .doesNotContain("TS Alice", "TS Bob");
  }
}
