/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@ComponentId("all_the_field_types_view")
public class AllTheTypesView extends View {

  // indexable fields of AllTheTypesKvEntity.AllTheTypes
  public record AllTheQueryableTypes(
      int intValue,
      long longValue,
      float floatValue,
      double doubleValue,
      boolean booleanValue,
      String stringValue,
      Integer wrappedInt,
      Long wrappedLong,
      Float wrappedFloat,
      Double wrappedDouble,
      Boolean wrappedBoolean,
      // time and date types
      Instant instant,
      ZonedDateTime zonedDateTime,
      // other more or less complex types
      Optional<String> optionalString,
      List<String> repeatedString,
      // AllTheTypesKvEntity.AllTheTypes.nestedMessage.email
      // Note: nested classes not supported in query parameter
      String nestedEmail
      // FIXME indexing on enums not supported yet: AllTheTypesKvEntity.AnEnum anEnum
      // Note: recursive structures cannot be indexed
  ) {}


  @Consume.FromKeyValueEntity(AllTheTypesKvEntity.class)
  public static class Events extends TableUpdater<AllTheTypesKvEntity.AllTheTypes> { }

  @Query("""
      SELECT * FROM events WHERE
      intValue = :intValue AND
      longValue = :longValue AND
      floatValue = :floatValue AND
      doubleValue = :doubleValue AND
      booleanValue = :booleanValue AND
      stringValue = :stringValue AND
      wrappedInt = :wrappedInt AND
      wrappedLong = :wrappedLong AND
      wrappedFloat = :wrappedFloat AND
      wrappedDouble = :wrappedDouble AND
      wrappedBoolean = :wrappedBoolean AND
      instant = :instant AND
      zonedDateTime = :zonedDateTime AND
      optionalString = :optionalString AND
      repeatedString = :repeatedString AND
      nestedMessage.email = :nestedEmail
      """)
  public QueryStreamEffect<AllTheTypesKvEntity.AllTheTypes> specificRow(AllTheQueryableTypes query) { return queryStreamResult(); }

  @Query("SELECT * FROM events")
  public QueryStreamEffect<AllTheTypesKvEntity.AllTheTypes> allRows() {
    return queryStreamResult();
  }

  public record CountResult(long count) {}
  @Query("SELECT COUNT(*) FROM events")
  public QueryEffect<CountResult> countRows() {
    return queryResult();
  }

  public record InstantRequest(Instant instant) {}
  @Query("SELECT * FROM events WHERE instant < :instant")
  public QueryStreamEffect<AllTheTypesKvEntity.AllTheTypes> compareInstant(InstantRequest request) { return queryStreamResult(); }

  public record GroupResult(List<AllTheTypesKvEntity.AllTheTypes> grouped, long totalCount) {}
  @Query("SELECT * AS grouped, total_count() FROM events GROUP BY intValue")
  public QueryStreamEffect<GroupResult> groupQuery() { return queryStreamResult(); }

  @Query("SELECT * FROM events WHERE optionalString IS NOT NULL AND nestedMessage.email IS NOT NULL")
  public QueryStreamEffect<AllTheTypesKvEntity.AllTheTypes> nullableQuery() {
    return queryStreamResult();
  }

  public record PageRequest(String pageToken) {}
  public record Page(List<AllTheTypesKvEntity.AllTheTypes> entries, String nextPageToken, boolean hasMore) { }

  @Query("""
      SELECT * AS entries, next_page_token() AS nextPageToken, has_more() AS hasMore
      FROM events
      OFFSET page_token_offset(:pageToken)
      LIMIT 10
      """)
  public QueryEffect<Page> paging(PageRequest request) {
    return queryResult();
  }

  public record BeforeRequest(Instant instant) {}

  @Query("SELECT * FROM events WHERE zonedDateTime < :instant")
  public QueryStreamEffect<AllTheTypesKvEntity.AllTheTypes> beforeInstant(BeforeRequest query)  { return queryStreamResult(); }
}
