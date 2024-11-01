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

@ComponentId("users")
public class UsersView extends View {

  @Consume.FromKeyValueEntity(UserEntity.class)
  public static class Users extends TableUpdater<User> { }

  public static QueryByEmailParam byEmailParam(String email) {
    return new QueryByEmailParam(email);
  }

  public static QueryByNameParam byNameParam(String name) {
    return new QueryByNameParam(name);
  }

  public record QueryByEmailParam(String email) {}
  public record QueryByNameParam(String name) {}

  @Query("SELECT * FROM users WHERE email = :email")
  public QueryEffect<User> getUserByEmail(QueryByEmailParam param) {
    return queryResult();
  }

  @Query("SELECT * FROM users WHERE name = :name")
  public QueryEffect<User> getUserByName(QueryByNameParam param) {
    return queryResult();
  }
}
