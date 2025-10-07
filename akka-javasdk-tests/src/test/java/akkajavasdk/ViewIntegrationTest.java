/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.client.EventSourcedEntityClient;
import akka.javasdk.client.NoEntryFoundException;
import akka.javasdk.testkit.TestKitSupport;
import akka.stream.javadsl.Sink;
import akkajavasdk.components.eventsourcedentities.counter.Counter;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.keyvalueentities.user.AssignedCounterEntity;
import akkajavasdk.components.keyvalueentities.user.User;
import akkajavasdk.components.keyvalueentities.user.UserEntity;
import akkajavasdk.components.views.AllTheTypesKvEntity;
import akkajavasdk.components.views.AllTheTypesView;
import akkajavasdk.components.views.UserCounter;
import akkajavasdk.components.views.UserCounters;
import akkajavasdk.components.views.UserCountersView;
import akkajavasdk.components.views.counter.CountersByValueSubscriptions;
import akkajavasdk.components.views.counter.CountersByValueWithIgnore;
import akkajavasdk.components.views.hierarchy.HierarchyCountersByValue;
import akkajavasdk.components.views.user.UserWithVersion;
import akkajavasdk.components.views.user.UserWithVersionView;
import akkajavasdk.components.views.user.UsersByEmailAndName;
import akkajavasdk.components.views.user.UsersByName;
import akkajavasdk.components.views.user.UsersByPrimitives;
import akkajavasdk.components.views.user.UsersView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class ViewIntegrationTest extends TestKitSupport {

  /* Note: when changing test view queries/types so that they are invalid
   *       The failure is not shown by log capturing (happens in beforeAll)
   *       so will need changing logback-test.xml to show useful information.
   */

  private String newId() {
    return UUID.randomUUID().toString();
  }

  private static final BigDecimal PI_BIG_DECIMAL =
      new BigDecimal("3.14159265358979323846264338327950");

  @Test
  public void verifyTransformedUserViewWiring() {

    var id = newId();
    var email = id + "@example.com";
    var user = new TestUser(id, email, "JohnDoe");

    createUser(user);

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(15, TimeUnit.of(SECONDS))
        .until(() -> getUserByEmail(user.email()).version, new IsEqual(1));

    updateUser(user.withName("JohnDoeJr"));

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(15, TimeUnit.of(SECONDS))
        .until(() -> getUserByEmail(user.email()).version, new IsEqual(2));
  }

  @Test
  public void verifyViewIsNotSubscribedToMultiplyAndRouterIgnores() {

    var entityId = newId();
    EventSourcedEntityClient counterClient = componentClient.forEventSourcedEntity(entityId);
    counterClient.method(CounterEntity::increase).invoke(1);
    counterClient.method(CounterEntity::times).invoke(2);
    Integer counterGet = counterClient.method(CounterEntity::increase).invoke(1);

    assertThat(counterGet).isEqualTo(1 * 2 + 1);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var byValue =
                  await(
                      componentClient
                          .forView()
                          .method(CountersByValueWithIgnore::getCounterByValue)
                          .invokeAsync(CountersByValueWithIgnore.queryParam(2)));

              assertThat(byValue.value()).isEqualTo(1 + 1);
            });
  }

  @Test
  public void verifyHierarchyView() {

    var emptyCounter =
        await(
            componentClient
                .forView()
                .method(HierarchyCountersByValue::getCounterByValue)
                .invokeAsync(201));

    assertThat(emptyCounter).isEmpty();

    var esId = newId();
    await(
        componentClient
            .forEventSourcedEntity(esId)
            .method(CounterEntity::increase)
            .invokeAsync(201));

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(15, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var byValue =
                  await(
                      componentClient
                          .forView()
                          .method(HierarchyCountersByValue::getCounterByValue)
                          .invokeAsync(201));

              assertThat(byValue).hasValue(new Counter(201));
            });
  }

  @Test
  public void verifyCounterViewMultipleSubscriptions() {

    var id1 = newId();
    await(
        componentClient.forEventSourcedEntity(id1).method(CounterEntity::increase).invokeAsync(74));

    var id2 = newId();
    await(
        componentClient.forEventSourcedEntity(id2).method(CounterEntity::increase).invokeAsync(74));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .until(
            () ->
                await(
                        componentClient
                            .forView()
                            .method(CountersByValueSubscriptions::getCounterByValue)
                            .invokeAsync(new CountersByValueSubscriptions.QueryParameters(74)))
                    .counters()
                    .size(),
            new IsEqual<>(2));
  }

  @Test
  public void verifyAllTheFieldTypesView() throws Exception {
    // see that we can persist and read a row with all fields, no indexed columns
    var id = newId();
    var row =
        new AllTheTypesKvEntity.AllTheTypes(
            1,
            2L,
            3F,
            4D,
            true,
            "text",
            5,
            6L,
            7F,
            8D,
            false,
            Instant.now(),
            // Note: we turn it into a timestamp internally, so the specific TZ is lost (but the
            // exact point in time stays the same)
            ZonedDateTime.now().withZoneSameInstant(ZoneId.of("Z")),
            Optional.of("optional"),
            List.of("text", "text2"),
            new AllTheTypesKvEntity.ByEmail("test@example.com"),
            AllTheTypesKvEntity.AnEnum.THREE,
            new AllTheTypesKvEntity.Recursive(
                new AllTheTypesKvEntity.Recursive(null, "level2"), "level1"),
            PI_BIG_DECIMAL);
    await(
        componentClient.forKeyValueEntity(id).method(AllTheTypesKvEntity::store).invokeAsync(row));

    // just as row payload
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var rows =
                  await(
                      componentClient.forView().stream(AllTheTypesView::allRows)
                          .source()
                          .runWith(Sink.seq(), testKit.getMaterializer()));

              assertThat(rows).hasSize(1);
            });

    // cover indexable column types
    var query =
        new AllTheTypesView.AllTheQueryableTypes(
            row.intValue(),
            row.longValue(),
            row.floatValue(),
            row.doubleValue(),
            row.booleanValue(),
            row.stringValue(),
            row.wrappedInt(),
            row.wrappedLong(),
            row.wrappedFloat(),
            row.wrappedDouble(),
            row.wrappedBoolean(),
            row.instant(),
            row.zonedDateTime(),
            row.optionalString(),
            row.repeatedString(),
            row.nestedMessage().email(),
            PI_BIG_DECIMAL);
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var rows =
                  await(
                      componentClient.forView().stream(AllTheTypesView::specificRow)
                          .source(query)
                          .runWith(Sink.seq(), testKit.getMaterializer()));

              assertThat(rows).hasSize(1);
            });

    // timestamp comparisons
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var rows =
                  await(
                      componentClient.forView().stream(AllTheTypesView::beforeInstant)
                          .source(
                              new AllTheTypesView.BeforeRequest(
                                  row.zonedDateTime().toInstant().plus(4, HOURS)))
                          .runWith(Sink.seq(), testKit.getMaterializer()));

              assertThat(rows).hasSize(1);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var result =
                  await(componentClient.forView().method(AllTheTypesView::countRows).invokeAsync());

              assertThat(result.count()).isEqualTo(1);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var rows =
                  await(
                      componentClient.forView().stream(AllTheTypesView::compareInstant)
                          .source(new AllTheTypesView.InstantRequest(Instant.now().minus(3, DAYS)))
                          .runWith(Sink.seq(), testKit.getMaterializer()));

              assertThat(rows).hasSize(1);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var rows =
                  await(
                      componentClient.forView().stream(AllTheTypesView::groupQuery)
                          .source()
                          .runWith(Sink.seq(), testKit.getMaterializer()));

              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().grouped()).hasSize(1);
              assertThat(rows.getFirst().grouped().getFirst()).isEqualTo(row);
              assertThat(rows.getFirst().totalCount()).isEqualTo(1L);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var rows =
                  await(
                      componentClient.forView().stream(AllTheTypesView::projectedGroupQuery)
                          .source()
                          .runWith(Sink.seq(), testKit.getMaterializer()));

              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().groupedStringValues()).hasSize(1);
              assertThat(rows.getFirst().groupedStringValues().getFirst())
                  .isEqualTo(row.stringValue());
              assertThat(rows.getFirst().totalCount()).isEqualTo(1L);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var rows =
                  await(
                      componentClient.forView().stream(AllTheTypesView::nullableQuery)
                          .source()
                          .runWith(Sink.seq(), testKit.getMaterializer()));

              assertThat(rows).hasSize(1);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var page =
                  await(
                      componentClient
                          .forView()
                          .method(AllTheTypesView::paging)
                          .invokeAsync(new AllTheTypesView.PageRequest("")));

              assertThat(page.entries()).hasSize(1);
              assertThat(page.hasMore()).isFalse();
            });
  }

  @Disabled // pending primitive query parameters working
  @Test
  public void shouldAcceptPrimitivesForViewQueries() {

    TestUser user1 = new TestUser(newId(), "john654321@doe.com", "Bob2");
    TestUser user2 = new TestUser(newId(), "john7654321@doe.com", "Bob3");
    createUser(user1);
    createUser(user2);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var resultByString =
                  await(
                      componentClient
                          .forView()
                          .method(UsersByPrimitives::getUserByString)
                          .invokeAsync(user1.email()));
              assertThat(resultByString.users()).isNotEmpty();

              var resultByInt =
                  await(
                      componentClient
                          .forView()
                          .method(UsersByPrimitives::getUserByInt)
                          .invokeAsync(123));
              assertThat(resultByInt.users()).isNotEmpty();

              var resultByLong =
                  await(
                      componentClient
                          .forView()
                          .method(UsersByPrimitives::getUserByLong)
                          .invokeAsync(321l));
              assertThat(resultByLong.users()).isNotEmpty();

              var resultByDouble =
                  await(
                      componentClient
                          .forView()
                          .method(UsersByPrimitives::getUserByDouble)
                          .invokeAsync(12.3d));
              assertThat(resultByDouble.users()).isNotEmpty();

              var resultByBoolean =
                  await(
                      componentClient
                          .forView()
                          .method(UsersByPrimitives::getUserByBoolean)
                          .invokeAsync(true));
              assertThat(resultByBoolean.users()).isNotEmpty();

              var resultByEmails =
                  await(
                      componentClient
                          .forView()
                          .method(UsersByPrimitives::getUserByEmails)
                          .invokeAsync(List.of(user1.email(), user2.email())));
              assertThat(resultByEmails.users()).hasSize(2);
            });
  }

  @Test
  public void shouldDeleteValueEntityAndDeleteViewsState() {

    TestUser user = new TestUser(newId(), "john123@doe.com", "Bob123");
    createUser(user);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(15, TimeUnit.of(SECONDS))
        .until(() -> getUserByEmail(user.email()).version, new IsEqual(1));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(15, TimeUnit.of(SECONDS))
        .until(() -> getUsersByName(user.name()).size(), new IsEqual(1));

    deleteUser(user);

    Awaitility.await()
        .atMost(15, TimeUnit.of(SECONDS))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              var ex =
                  failed(
                      componentClient
                          .forView()
                          .method(UserWithVersionView::getUser)
                          .invokeAsync(UserWithVersionView.queryParam(user.email())));
              assertThat(ex).isInstanceOf(NoEntryFoundException.class);
            });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(15, TimeUnit.of(SECONDS))
        .until(() -> getUsersByName(user.name()).size(), new IsEqual(0));
  }

  @Test
  public void verifyFindUsersByEmailView() {

    TestUser user = new TestUser(newId(), "john3@doe.com", "JohnDoe");
    createUser(user);

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var byEmail =
                  await(
                      componentClient
                          .forView()
                          .method(UsersView::getUserByEmail)
                          .invokeAsync(UsersView.byEmailParam(user.email())));

              assertThat(byEmail.email).isEqualTo(user.email());
            });
  }

  @Test
  public void verifyFindUsersByNameView() {

    TestUser user = new TestUser(newId(), "john4@doe.com", "JohnDoe2");
    createUser(user);

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var byName = getUsersByName(user.name()).getFirst();
              assertThat(byName.name).isEqualTo(user.name());
            });
  }

  @Test
  public void verifyFindUsersByEmailAndNameView() {

    TestUser user = new TestUser(newId(), "john3@doe.com2", "JohnDoe2");
    createUser(user);

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var request = new UsersByEmailAndName.QueryParameters(user.email(), user.name());

              var byEmail =
                  await(
                      componentClient
                          .forView()
                          .method(UsersByEmailAndName::getUsers)
                          .invokeAsync(request));

              assertThat(byEmail.email).isEqualTo(user.email());
              assertThat(byEmail.name).isEqualTo(user.name());
            });
  }

  @Test
  public void verifyMultiTableViewForUserCounters() {

    TestUser alice = new TestUser(newId(), "alice@foo.com", "Alice Foo");
    TestUser bob = new TestUser(newId(), "bob@bar.com", "Bob Bar");

    createUser(alice);
    createUser(bob);

    assignCounter("c1", alice.id());
    assignCounter("c2", bob.id());
    assignCounter("c3", alice.id());
    assignCounter("c4", bob.id());

    increaseCounter("c1", 11);
    increaseCounter("c2", 22);
    increaseCounter("c3", 33);
    increaseCounter("c4", 44);

    // the view is eventually updated

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .until(() -> getUserCounters(alice.id()).counters.size(), new IsEqual<>(2));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .until(() -> getUserCounters(bob.id()).counters.size(), new IsEqual<>(2));

    UserCounters aliceCounters = getUserCounters(alice.id());
    assertThat(aliceCounters.id).isEqualTo(alice.id());
    assertThat(aliceCounters.email).isEqualTo(alice.email());
    assertThat(aliceCounters.name).isEqualTo(alice.name());
    assertThat(aliceCounters.counters)
        .containsOnly(new UserCounter("c1", 11), new UserCounter("c3", 33));

    UserCounters bobCounters = getUserCounters(bob.id());

    assertThat(bobCounters.id).isEqualTo(bob.id());
    assertThat(bobCounters.email).isEqualTo(bob.email());
    assertThat(bobCounters.name).isEqualTo(bob.name());
    assertThat(bobCounters.counters)
        .containsOnly(new UserCounter("c2", 22), new UserCounter("c4", 44));
  }

  private void createUser(TestUser user) {
    Ok userCreation =
        await(
            componentClient
                .forKeyValueEntity(user.id())
                .method(UserEntity::createOrUpdateUser)
                .invokeAsync(new UserEntity.CreatedUser(user.name(), user.email())));
    assertThat(userCreation).isEqualTo(Ok.instance);
  }

  private void updateUser(TestUser user) {
    Ok userUpdate =
        await(
            componentClient
                .forKeyValueEntity(user.id())
                .method(UserEntity::createOrUpdateUser)
                .invokeAsync(new UserEntity.CreatedUser(user.name(), user.email())));

    assertThat(userUpdate).isEqualTo(Ok.instance);
  }

  private UserWithVersion getUserByEmail(String email) {
    return await(
        componentClient
            .forView()
            .method(UserWithVersionView::getUser)
            .invokeAsync(UserWithVersionView.queryParam(email)));
  }

  private void increaseCounter(String id, int value) {
    await(
        componentClient
            .forEventSourcedEntity(id)
            .method(CounterEntity::increase)
            .invokeAsync(value));
  }

  private void assignCounter(String id, String assignee) {
    await(
        componentClient
            .forKeyValueEntity(id)
            .method(AssignedCounterEntity::assign)
            .invokeAsync(assignee));
  }

  private UserCounters getUserCounters(String userId) {
    return await(
        componentClient
            .forView()
            .method(UserCountersView::get)
            .invokeAsync(UserCountersView.queryParam(userId)));
  }

  private List<User> getUsersByName(String name) {
    return await(
            componentClient
                .forView()
                .method(UsersByName::getUsers)
                .invokeAsync(new UsersByName.QueryParameters(name)))
        .users();
  }

  private void deleteUser(TestUser user) {
    Ok userDeleted =
        await(
            componentClient
                .forKeyValueEntity(user.id())
                .method(UserEntity::deleteUser)
                .invokeAsync(new UserEntity.Delete()));
    assertThat(userDeleted).isEqualTo(Ok.instance);
  }
}
