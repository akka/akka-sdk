package demo.multiagent.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface PreferencesEvent {
  @TypeName("preference-added")
  record PreferenceAdded(String preference) implements PreferencesEvent {
  }
}
