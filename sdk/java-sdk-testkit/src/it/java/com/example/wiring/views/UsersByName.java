/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import akka.platform.javasdk.view.TableUpdater;
import com.example.wiring.keyvalueentities.user.User;
import com.example.wiring.keyvalueentities.user.UserEntity;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;

import java.util.List;

@ComponentId("users_by_name")
public class UsersByName extends View {

  @Consume.FromKeyValueEntity(value = UserEntity.class, handleDeletes = true)
  public static class Users extends TableUpdater<User> { }

  public record QueryParameters(String name) {}
  public record UserList(List<User> users) {}

  @Query("SELECT * AS users FROM users WHERE name = :name")
  public QueryEffect<UserList> getUsers(QueryParameters params) {
    return queryResult();
  }
}
