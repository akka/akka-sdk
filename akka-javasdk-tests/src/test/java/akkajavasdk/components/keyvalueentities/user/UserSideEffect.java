/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import java.util.concurrent.ConcurrentHashMap;

public class UserSideEffect {

  static final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

  static void addUser(String id, User user) {
    users.put(id, user);
  }

  static void removeUser(String id) {
    users.remove(id);
  }

  public static ConcurrentHashMap<String, User> getUsers() {
    return users;
  }
}
