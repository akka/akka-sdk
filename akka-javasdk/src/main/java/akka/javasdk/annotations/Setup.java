/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import akka.javasdk.ServiceSetup;
import java.lang.annotation.*;

/**
 * Mark a class as a central configuration point for an entire service. Note that a service will
 * also work without any such config point.
 *
 * <p>The class can optionally implement {@link ServiceSetup} with predefined lifecycle hooks.
 *
 * <p>May only be used on one class per service.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Setup {}
