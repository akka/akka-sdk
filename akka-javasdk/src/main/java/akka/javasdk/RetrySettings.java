/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import java.time.Duration;

public class RetrySettings {

  public interface RetrySettingsBuilder {

    /**
     * Set fixed delay between attempts.
     */
    RetrySettings withFixedDelay(Duration fixedDelay);

    /**
     * Set exponential backoff between attempts.
     */
    RetrySettings withBackoff(Duration minBackoff, Duration maxBackoff, double randomFactor);

    /**
     * Use predifined (based on the number of attempts) exponential backoff between attempts.
     */
    RetrySettings withBackoff();
  }

  /**
   * Set the number of attempts to make.
   */
  public final static RetrySettingsBuilder attempts(int attempts) {
    return new akka.javasdk.impl.RetrySettingsBuilder(attempts);
  }
}
