package demo.ui;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.autonomous.Notification;
import demo.ui.api.RunNotificationEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Pure-classifier test for the tier mapping rule documented in
 * {@code contracts/run-notifications.md}. No TestKit needed — exercises a static method against
 * synthetic Notification instances.
 */
public class TierClassifierTest {

  @Test
  public void healthyEvents() {
    assertThat(RunNotificationEndpoint.tierFor(new Notification.Activated())).isEqualTo(
      "healthy"
    );
    assertThat(RunNotificationEndpoint.tierFor(new Notification.Deactivated())).isEqualTo(
      "healthy"
    );
    assertThat(
      RunNotificationEndpoint.tierFor(new Notification.IterationStarted())
    ).isEqualTo("healthy");
    assertThat(
      RunNotificationEndpoint.tierFor(new Notification.TaskAssigned("t1"))
    ).isEqualTo("healthy");
    assertThat(
      RunNotificationEndpoint.tierFor(new Notification.TaskStarted("t1", "name"))
    ).isEqualTo("healthy");
    assertThat(
      RunNotificationEndpoint.tierFor(new Notification.TaskCompleted("t1", "name"))
    ).isEqualTo("healthy");
  }

  @Test
  public void struggleEvents() {
    assertThat(
      RunNotificationEndpoint.tierFor(
        new Notification.IterationFailed(
          "llm timeout",
          java.util.Optional.empty(),
          java.util.Optional.empty()
        )
      )
    ).isEqualTo("struggle");
    assertThat(
      RunNotificationEndpoint.tierFor(
        new Notification.TaskResultRejected("t1", "name", "rule-x")
      )
    ).isEqualTo("struggle");
    assertThat(
      RunNotificationEndpoint.tierFor(
        new Notification.TaskDependencyWait("t1", java.util.List.of("dep1"))
      )
    ).isEqualTo("struggle");
  }

  @Test
  public void terminalFailureEvents() {
    assertThat(
      RunNotificationEndpoint.tierFor(new Notification.TaskFailed("t1", "name", "boom"))
    ).isEqualTo("terminal_failure");
    assertThat(
      RunNotificationEndpoint.tierFor(
        new Notification.TaskCancelled("t1", "name", "max iterations")
      )
    ).isEqualTo("terminal_failure");
    assertThat(
      RunNotificationEndpoint.tierFor(new Notification.Stopped("operator"))
    ).isEqualTo("terminal_failure");
  }

  @Test
  public void autoStoppedIsHealthy() {
    assertThat(
      RunNotificationEndpoint.tierFor(new Notification.Stopped("auto-stopped"))
    ).isEqualTo("healthy");
  }
}
