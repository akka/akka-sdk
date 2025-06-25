package agent_guide.part2;

import akka.javasdk.annotations.TypeName;

public sealed interface PreferencesEvent {
  @TypeName("preference-added")
  record PreferenceAdded(String preference) implements PreferencesEvent {
  }
}
