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

import java.util.List;

@ComponentId("users_by_name")
@Table("users_by_name")
@Consume.FromKeyValueEntity(value = UserEntity.class, handleDeletes = true)
public class UsersByName extends View<User> {

  public record QueryParameters(String name) {}
  public record UserList(List<User> users) {}

  @Query("SELECT * AS users FROM users_by_name WHERE name = :name")
  public UserList getUsers(QueryParameters params) {
    return null;
  }
}
