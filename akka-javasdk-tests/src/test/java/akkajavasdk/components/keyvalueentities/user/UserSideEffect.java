/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import java.util.concurrent.ConcurrentHashMap;

public class UserSideEffect {

  static final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
  static final ConcurrentHashMap<String, String> metas = new ConcurrentHashMap<>();

  static void addUser(String id, User user) {
    users.put(id, user);
  }

  static void removeUser(String id) {
    users.remove(id);
  }

  public static ConcurrentHashMap<String, User> getUsers() {
    return users;
  }

  static void setMeta(String id, String meta) {
    metas.put(id, meta);
  }

  public static ConcurrentHashMap<String, String> getMetas() {
    return metas;
  }
}
