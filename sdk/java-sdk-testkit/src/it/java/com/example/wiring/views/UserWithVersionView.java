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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("user_view")
public class UserWithVersionView extends View<UserWithVersion> {

  private static final Logger logger = LoggerFactory.getLogger(UserWithVersionView.class);

  public record QueryParameters(String email) {}

  public static QueryParameters queryParam(String email) {
    return new QueryParameters(email);
  }

  @Query("SELECT * FROM user_view WHERE email = :email")
  public UserWithVersion getUser(QueryParameters params) {
    return null;
  }

  @Consume.FromKeyValueEntity(UserEntity.class)
  public Effect<UserWithVersion> onChange(User user) {
    if (viewState() == null) return effects().updateState(new UserWithVersion(user.email, 1));
    else return effects().updateState(new UserWithVersion(user.email, viewState().version + 1));
  }

  @Consume.FromKeyValueEntity(value = UserEntity.class, handleDeletes = true)
  public Effect<UserWithVersion> onDelete() {
    logger.info("Deleting user with email={}", viewState().email);
    return effects().deleteState();
  }

}
