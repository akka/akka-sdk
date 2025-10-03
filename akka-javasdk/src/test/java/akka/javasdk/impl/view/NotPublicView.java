/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.testmodels.keyvalueentity.User;
import akka.javasdk.testmodels.keyvalueentity.UserEntity;
import akka.javasdk.view.TableUpdater;

// not public and thus need to be in the same package as the corresponding test
@Component(id = "users_view")
class NotPublicView {

  @Consume.FromKeyValueEntity(UserEntity.class)
  public static class Users extends TableUpdater<User> {}

  public record QueryParameters(String email) {}

  @Query("SELECT * FROM users WHERE email = :email")
  public User getUser(QueryParameters email) {
    return null;
  }
}
