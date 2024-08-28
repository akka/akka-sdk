/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;


import akka.javasdk.ServiceSetup;

import java.lang.annotation.*;

/**
 * Mark a class as a central configuration point for an entire service.
 * Note that a Kalix service will also work without any such config point.
 * <p>
 * Annotate the class with @Acl as well for defining a default ACL for the entire service
 * <p>
 * The class can optionally implement {@link ServiceSetup} for customizing various service setup
 * level aspects.
 * <p>
 * May only be used on one class per Kalix service.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Setup {
}
