package demo.multiagent.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import demo.multiagent.domain.Preferences;
import demo.multiagent.domain.PreferencesEvent;

import java.util.List;

@ComponentId("preferences")
public class PreferencesEntity
    extends EventSourcedEntity<Preferences, PreferencesEvent> {

  public record AddPreference(String preference) {}

  public Effect<Done> addPreference(AddPreference command) {
    return effects()
        .persist(new PreferencesEvent.PreferenceAdded(command.preference()))
        .thenReply(__ -> Done.done());
  }

  public Effect<Preferences> getPreferences() {
    List<String> prefs;
    if (currentState() == null) {
      return effects().reply(new Preferences(List.of()));
    } else {
      return effects().reply(currentState());
    }
  }

  @Override
  public Preferences applyEvent(PreferencesEvent event) {
    return switch (event) {
      case PreferencesEvent.PreferenceAdded evt -> currentState().addPreference(evt.preference());
    };
  }

}
