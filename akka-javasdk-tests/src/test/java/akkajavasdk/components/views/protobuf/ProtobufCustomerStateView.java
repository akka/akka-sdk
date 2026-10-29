/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.protobuf;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akkajavasdk.components.keyvalueentities.protobuf.ProtobufCustomerKvEntity;
import akkajavasdk.protocol.SerializationTestProtos.CustomerContactResult;
import akkajavasdk.protocol.SerializationTestProtos.CustomerState;
import akkajavasdk.protocol.SerializationTestProtos.CustomerStateList;
import akkajavasdk.protocol.SerializationTestProtos.CustomerStateResult;
import akkajavasdk.protocol.SerializationTestProtos.CustomersByCreationTimeQuery;
import akkajavasdk.protocol.SerializationTestProtos.SimpleMessage;

/**
 * A view that uses protobuf messages for state, query parameter, and return value. This exercises
 * protobuf-as-JSON serialization for views (which store data as JSONB).
 */
@Component(id = "protobuf_customer_state_view")
public class ProtobufCustomerStateView extends View {

  @Consume.FromKeyValueEntity(ProtobufCustomerKvEntity.class)
  public static class Customers extends TableUpdater<CustomerState> {}

  @Query("SELECT * FROM customers WHERE name = :text")
  public QueryEffect<CustomerState> getCustomerByName(SimpleMessage params) {
    return queryResult();
  }

  @Query("SELECT * as customers FROM customers WHERE review_date IS NULL")
  public QueryEffect<CustomerStateList> getCustomersWithoutReviewDate() {
    return queryResult();
  }

  @Query("SELECT * as customers FROM customers WHERE review_date IS NOT NULL")
  public QueryEffect<CustomerStateList> getCustomersWithReviewDate() {
    return queryResult();
  }

  @Query("SELECT * as customers FROM customers WHERE nickname IS NULL")
  public QueryEffect<CustomerStateList> getCustomersWithoutNickname() {
    return queryResult();
  }

  @Query("SELECT * as customers FROM customers WHERE nickname = :text")
  public QueryEffect<CustomerStateList> getCustomersByNickname(SimpleMessage params) {
    return queryResult();
  }

  @Query("SELECT * as customers FROM customers WHERE contact IS NULL")
  public QueryEffect<CustomerStateList> getCustomersWithoutContact() {
    return queryResult();
  }

  @Query("SELECT * as customers FROM customers WHERE preferred_email IS NULL")
  public QueryEffect<CustomerStateList> getCustomersWithoutPreferredEmail() {
    return queryResult();
  }

  @Query("SELECT * as customers FROM customers WHERE preferred_email = :text")
  public QueryEffect<CustomerStateList> getCustomersByPreferredEmail(SimpleMessage params) {
    return queryResult();
  }

  @Query("SELECT * as customers FROM customers WHERE display_name IS NULL")
  public QueryEffect<CustomerStateList> getCustomersWithoutDisplayName() {
    return queryResult();
  }

  @Query("SELECT * as customers FROM customers WHERE display_name = :text")
  public QueryEffect<CustomerStateList> getCustomersByDisplayName(SimpleMessage params) {
    return queryResult();
  }

  @Query("SELECT * AS customer FROM customers WHERE name = :text")
  public QueryEffect<CustomerStateResult> getCustomerAsNestedMessage(SimpleMessage params) {
    return queryResult();
  }

  @Query("SELECT (name, email) AS contact FROM customers WHERE name = :text")
  public QueryEffect<CustomerContactResult> getCustomerContact(SimpleMessage params) {
    return queryResult();
  }

  @Query("SELECT * as customers FROM customers WHERE created_at >= :created_after")
  public QueryEffect<CustomerStateList> getCustomersCreatedAfter(
      CustomersByCreationTimeQuery params) {
    return queryResult();
  }
}
