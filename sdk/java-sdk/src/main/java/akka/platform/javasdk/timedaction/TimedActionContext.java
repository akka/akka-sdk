/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.timedaction;

import akka.platform.javasdk.Context;

/**
 * Creation context for {@link TimedAction} components.
 *
 * <p>This may be accepted as an argument to the constructor of a TimedAction.
 */
public interface TimedActionContext extends Context {}
