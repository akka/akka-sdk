package agent_guide.part2;

// tag::class[]
import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

import java.util.ArrayList;
import java.util.List;

@ComponentId("preferences") // <2>
public class PreferencesEntity
    extends EventSourcedEntity<PreferencesEntity.State, PreferencesEntity.Event> { // <1>

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

  public Effect<Done> addPreference(AddPreference command) { // <3>
    return effects()
        .persist(new Event.PreferenceAdded(command.preference()))
        .thenReply(__ -> Done.done());
  }

  public Effect<AllPreferences> getPreferences() { // <4>
    return effects().reply(new AllPreferences(currentState().preferences()));
  }

  @Override
  public State applyEvent(Event event) { // <5>
    return switch (event) {
      case Event.PreferenceAdded evt -> currentState().addPreference(evt.preference());
    };
  }

}
// end::class[]
