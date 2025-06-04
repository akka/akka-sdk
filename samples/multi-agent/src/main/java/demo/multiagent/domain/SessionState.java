package demo.multiagent.domain;

import java.util.ArrayList;
import java.util.List;

public record SessionState(List<SessionMessage> messages) {
  public static SessionState empty() {
    return new SessionState(new ArrayList<>());
  }

  public SessionState add(SessionMessage sessionMessage) {
    messages.add(sessionMessage);
    return this;
  }
}
