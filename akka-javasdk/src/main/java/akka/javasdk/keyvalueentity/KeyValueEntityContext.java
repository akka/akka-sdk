/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.keyvalueentity;

import akka.javasdk.EntityContext;

/**
 * Context information available during Key Value Entity construction and initialization.
 * This context provides access to entity metadata and configuration that is available
 * throughout the entity's lifecycle.
 * 
 * <p>The KeyValueEntityContext is typically injected into the entity constructor and can be
 * used to access the entity id and other contextual information needed during entity setup.
 * 
 * <p>Unlike {@link CommandContext}, this context is available during entity construction
 * and is not limited to command processing.
 */
public interface KeyValueEntityContext extends EntityContext {}
