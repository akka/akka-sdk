package demo.multiagent.application;

import akka.Done;
import akka.javasdk.testkit.EventSourcedTestKit;
import demo.multiagent.domain.Preferences;
import demo.multiagent.domain.PreferencesEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PreferencesEntityTest {

  @Test
  public void testAddPreference() {
    var testKit = EventSourcedTestKit.of("pref-1", PreferencesEntity::new);

    var result = testKit.method(PreferencesEntity::addPreference)
        .invoke(new PreferencesEntity.AddPreference("coffee"));

    assertThat(result.getReply()).isEqualTo(Done.done());

    var preferenceAdded = result.getNextEventOfType(PreferencesEvent.PreferenceAdded.class);
    assertThat(preferenceAdded.preference()).isEqualTo("coffee");

    assertThat(testKit.getState().entries()).containsExactly("coffee");
  }

  @Test
  public void testAddMultiplePreferences() {
    var testKit = EventSourcedTestKit.of("pref-2", PreferencesEntity::new);

    // Add first preference
    var result1 = testKit.method(PreferencesEntity::addPreference)
        .invoke(new PreferencesEntity.AddPreference("coffee"));
    assertThat(result1.getReply()).isEqualTo(Done.done());

    // Add second preference
    var result2 = testKit.method(PreferencesEntity::addPreference)
        .invoke(new PreferencesEntity.AddPreference("tea"));
    assertThat(result2.getReply()).isEqualTo(Done.done());

    // Add third preference
    var result3 = testKit.method(PreferencesEntity::addPreference)
        .invoke(new PreferencesEntity.AddPreference("juice"));
    assertThat(result3.getReply()).isEqualTo(Done.done());

    // Verify all events were persisted
    assertThat(testKit.getAllEvents()).hasSize(3);

    // Verify final state contains all preferences
    assertThat(testKit.getState().entries()).containsExactly("coffee", "tea", "juice");
  }

  @Test
  public void testGetPreferences() {
    var testKit = EventSourcedTestKit.of("pref-3", PreferencesEntity::new);

    // Initially empty
    var initialResult = testKit.method(PreferencesEntity::getPreferences).invoke();
    assertThat(initialResult.getReply()).isEqualTo(new Preferences(List.of()));

    // Add a preference
    testKit.method(PreferencesEntity::addPreference)
        .invoke(new PreferencesEntity.AddPreference("chocolate"));

    // Get preferences after adding
    var result = testKit.method(PreferencesEntity::getPreferences).invoke();
    assertThat(result.getReply().entries()).containsExactly("chocolate");
  }

  @Test
  public void testEmptyState() {
    var testKit = EventSourcedTestKit.of("pref-4", PreferencesEntity::new);

    assertThat(testKit.getState()).isEqualTo(new Preferences(List.of()));
    assertThat(testKit.getAllEvents()).isEmpty();
  }
}
