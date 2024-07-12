/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.keyvalueentities.user.User;
import com.example.wiring.keyvalueentities.user.UserEntity;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ViewId;
import akka.platform.javasdk.view.View;

@ViewId("users_by_email_and_name")
@Table("users_by_email_and_name")
@Consume.FromKeyValueEntity(UserEntity.class)
public class UsersByEmailAndName extends View<User> {

  public record QueryParameters(String email, String name) {}

  @Query("SELECT * FROM users_by_email_and_name WHERE email = :email AND name = :name")
  public User getUsers(QueryParameters params) {
    return null;
  }
}
