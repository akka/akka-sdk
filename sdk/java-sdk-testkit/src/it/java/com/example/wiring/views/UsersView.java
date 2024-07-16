/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.keyvalueentities.user.User;
import com.example.wiring.keyvalueentities.user.UserEntity;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;

@ComponentId("users")
@Table("users")
@Consume.FromKeyValueEntity(UserEntity.class)
public class UsersView extends View<User> {

  public static QueryByEmailParam byEmailParam(String email) {
    return new QueryByEmailParam(email);
  }

  public static QueryByNameParam byNameParam(String name) {
    return new QueryByNameParam(name);
  }

  public record QueryByEmailParam(String email) {}
  public record QueryByNameParam(String name) {}

  @Query("SELECT * FROM users WHERE email = :email")
  public User getUserByEmail(QueryByEmailParam param) {
    return null;
  }

  @Query("SELECT * FROM users WHERE name = :name")
  public User getUserByName(QueryByNameParam param) {
    return null;
  }
}
