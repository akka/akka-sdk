/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels;

import java.time.Instant;

enum Level {HIGH, LOW};

public record TimeEnum(Instant time, Level lev){}