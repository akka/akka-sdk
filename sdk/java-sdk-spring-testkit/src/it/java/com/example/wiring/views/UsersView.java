/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.valueentities.user.User;
import com.example.wiring.valueentities.user.UserEntity;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Consume;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

@ViewId("users")
@Table("users")
@Consume.FromValueEntity(UserEntity.class)
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
