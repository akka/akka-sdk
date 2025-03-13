/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables the replication filter feature for an Event Sourced Entity, allowing runtime control of
 * which regions participate in event replication.
 *
 * <p>After enabling this annotation, the entity will still replicate to all regions until the
 * regions are defined with the {@code updateReplicationFilter} effect.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableReplicationFilter {}
