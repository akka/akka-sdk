/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.valueentities.user.User;
import com.example.wiring.valueentities.user.UserEntity;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ViewId("user_view")
@Table("user_view")
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

  @Subscribe.ValueEntity(UserEntity.class)
  public UpdateEffect<UserWithVersion> onChange(User user) {
    if (viewState() == null) return effects().updateState(new UserWithVersion(user.email, 1));
    else return effects().updateState(new UserWithVersion(user.email, viewState().version + 1));
  }

  @Subscribe.ValueEntity(value = UserEntity.class, handleDeletes = true)
  public UpdateEffect<UserWithVersion> onDelete() {
    logger.info("Deleting user with email={}", viewState().email);
    return effects().deleteState();
  }

}
