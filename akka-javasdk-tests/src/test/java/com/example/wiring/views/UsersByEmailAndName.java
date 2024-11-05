/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import akka.javasdk.view.TableUpdater;
import com.example.wiring.keyvalueentities.user.User;
import com.example.wiring.keyvalueentities.user.UserEntity;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;

@ComponentId("users_by_email_and_name")
public class UsersByEmailAndName extends View {

  @Consume.FromKeyValueEntity(UserEntity.class)
  public static class Users extends TableUpdater<User> {}

  public record QueryParameters(String email, String name) {}

  @Query("SELECT * FROM users WHERE email = :email AND name = :name")
  public QueryEffect<User> getUsers(QueryParameters params) {
    return queryResult();
  }
}