/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated annotation method serves as an alias for the {@code value()} method.
 * <p>
 * When used in a custom annotation, this marker allows a field to act as an alternative to the {@code value()} field.
 * Only one of {@code value()} or the field annotated with {@code @ValueAlias} should be set at a time.
 * If both are set, an {@link IllegalArgumentException} should be thrown by utilities processing the annotation.
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>
 * &#64;interface MyAnnotation {
 *     String value() default "";
 *     &#64;ValueAlias
 *     String name() default "";
 * }
 * </pre>
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValueAlias {}