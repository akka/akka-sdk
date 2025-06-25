package agent_guide.part2;

import java.util.ArrayList;
import java.util.List;

public record Preferences(List<String> entries) {
  public Preferences addPreference(String preference) {
    var newEntries = new ArrayList<>(entries);
    newEntries.add(preference);
    return new Preferences(newEntries);
  }
}
