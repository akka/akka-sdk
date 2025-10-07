package agent_guide.part2;

// tag::all[]
import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import java.util.List;

@Component(id = "preferences") // <2>
public class PreferencesEntity extends EventSourcedEntity<Preferences, PreferencesEvent> { // <1>

  public record AddPreference(String preference) {}

  @Override
  public Preferences emptyState() {
    return new Preferences(List.of());
  }

  public Effect<Done> addPreference(AddPreference command) { // <3>
    return effects()
      .persist(new PreferencesEvent.PreferenceAdded(command.preference()))
      .thenReply(__ -> Done.done());
  }

  public Effect<Preferences> getPreferences() { // <4>
    return effects().reply(currentState());
  }

  @Override
  public Preferences applyEvent(PreferencesEvent event) { // <5>
    return switch (event) {
      case PreferencesEvent.PreferenceAdded evt -> currentState()
        .addPreference(evt.preference());
    };
  }
}
// end::all[]
