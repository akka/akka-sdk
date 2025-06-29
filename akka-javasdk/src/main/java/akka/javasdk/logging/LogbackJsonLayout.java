/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.logging;

/**
 * This Logback JSON layout uses the name {@code severity} (instead of {@code level}).
 *
 * <p>Eg. Stackdriver expects the log severity to be in a field called {@code severity}.
 *
 * <p>IMPORTANT: This class depends on the "logback-json-classic" library (organization
 * "ch.qos.logback.contrib") and the Jackson layout support requires "logback-jackson" (organization
 * "ch.qos.logback.contrib") which need to be added as dependencies.
 */
public final class LogbackJsonLayout extends ch.qos.logback.contrib.json.classic.JsonLayout {

  public LogbackJsonLayout() {
    setIncludeLevel(false);
  }

  @Override
  public void addCustomDataToJsonMap(
      java.util.Map<String, Object> map, ch.qos.logback.classic.spi.ILoggingEvent event) {
    add("severity", true, String.valueOf(event.getLevel()), map);
  }
}
