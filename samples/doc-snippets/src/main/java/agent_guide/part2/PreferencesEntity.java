package agent_guide.part2;

// tag::class[]
import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

import java.util.List;

@ComponentId("preferences") // <2>
public class PreferencesEntity
    extends EventSourcedEntity<Preferences, PreferencesEvent> { // <1>

  public record AddPreference(String preference) {}

  public Effect<Done> addPreference(AddPreference command) { // <3>
    return effects()
        .persist(new PreferencesEvent.PreferenceAdded(command.preference()))
        .thenReply(__ -> Done.done());
  }

  public Effect<Preferences> getPreferences() { // <4>
    List<String> prefs;
    if (currentState() == null) {
      return effects().reply(new Preferences(List.of()));
    } else {
      return effects().reply(currentState());
    }
  }

  @Override
  public Preferences applyEvent(PreferencesEvent event) { // <5>
    return switch (event) {
      case PreferencesEvent.PreferenceAdded evt -> currentState().addPreference(evt.preference());
    };
  }

}
// end::class[]
