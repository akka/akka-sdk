/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.view;

import akka.javasdk.testmodels.keyvalueentity.User;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collection;

public class UserCollection {

  public final Collection<User> users;

  @JsonCreator
  public UserCollection(@JsonProperty("users") Collection<User> users) {
    this.users = users;
  }
}
