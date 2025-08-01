/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class UserWithVersion {
  public final String email;
  public final int version;

  @JsonCreator
  public UserWithVersion(
      @JsonProperty("email") String email, @JsonProperty("version") int version) {
    this.email = email;
    this.version = version;
  }

  @Override
  public String toString() {
    return "UserWithVersion{" + "email='" + email + '\'' + ", version=" + version + '}';
  }
}
