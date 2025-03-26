/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import java.time.Duration;

public interface RetrySettings {

  interface RetrySettingsBuilder {

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
  RetrySettingsBuilder attempts(int attempts);
}
