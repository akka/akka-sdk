/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.Map;
import kalix.runtime.LogbackJsonLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Covers which configured sanitizer masks a log message, for the sanitizers in the test
 * application.conf. The runtime masks a log message while the log event is written, so these tests
 * write one through the layout that does it.
 */
@ExtendWith(Junit5LogCapturing.class)
public class LogSanitizerTest extends TestKitSupport {

  /** Formats one log event the way the runtime formats every log event of a deployed service. */
  private String write(String message) {
    var context = new LoggerContext();
    var layout = new LogbackJsonLayout();
    layout.setContext(context);
    layout.start();
    try {
      var event =
          new LoggingEvent(
              LogSanitizerTest.class.getName(),
              context.getLogger(LogSanitizerTest.class),
              Level.INFO,
              message,
              null,
              new Object[0]);
      event.setMDCPropertyMap(Map.of());
      return layout.doLayout(event);
    } finally {
      layout.stop();
    }
  }

  @Test
  public void shouldMaskWithTheSanitizerTheServiceImplements() {
    var written = write("customer logsecret asked for a refund");

    assertThat(written).contains("[log-masked]");
    assertThat(written).doesNotContain("logsecret");
  }

  @Test
  public void shouldMaskWithAnEntryThatNamesNoApplicationPoint() {
    var written = write("a sanitizesanitizesanitize in a log message");

    assertThat(written).contains("*".repeat("sanitizesanitizesanitize".length()));
    assertThat(written).doesNotContain("sanitizesanitizesanitize");
  }

  @Test
  public void shouldReachAnEntryThatOnlyMasksLogMessagesByName() {
    var masked =
        getSanitizerClient().sanitize("log-masking", "customer logsecret asked for a refund");

    assertThat(masked).isEqualTo("customer [log-masked] asked for a refund");
  }

  @Test
  public void shouldMaskWithAPredefinedGroupThatAnotherEntryAlsoSelects() {
    var written = write("mail me at someone@example.com");

    assertThat(written).contains("mail me at " + "*".repeat(19));
  }

  @Test
  public void shouldNotMaskWithAnEntryBoundToAnAgent() {
    var written = write("a scopedsecret in a log message");

    assertThat(written).contains("scopedsecret");
  }
}
