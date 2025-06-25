package demo.multiagent.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

import java.util.ArrayList;
import java.util.List;

@ComponentId("preferences")
public class PreferencesEntity
    extends EventSourcedEntity<PreferencesEntity.State, PreferencesEntity.Event> {

  public record State(List<String> preferences) {
    State addPreference(String preference) {
      var newPreferences = new ArrayList<>(preferences);
      newPreferences.add(preference);
      return new State(newPreferences);
    }
  }

  public record AddPreference(String preference) {}

  public record AllPreferences(List<String> preferences) {}

  public sealed interface Event {
    @TypeName("preference-added")
    record PreferenceAdded(String preference) implements Event{}
  }

  public Effect<Done> addPreference(AddPreference command) {
    return effects()
        .persist(new Event.PreferenceAdded(command.preference()))
        .thenReply(__ -> Done.done());
  }

  public Effect<AllPreferences> getPreferences() {
    List<String> prefs;
    if (currentState() == null) {
      prefs = new ArrayList<>();
    } else {
      prefs = currentState().preferences();
    }

    return effects().reply(new AllPreferences(prefs));
  }

  @Override
  public State applyEvent(Event event) {
    return switch (event) {
      case Event.PreferenceAdded evt -> currentState().addPreference(evt.preference());
    };
  }

}
