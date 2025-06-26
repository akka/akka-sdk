/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.*;


/**
 * Defines ACL configuration for a resource.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Acl {

  /**
   * Principals that are allowed to access this resource.
   * An incoming request must have at least one principal associated with it in this list to be allowed.
   */
  Matcher[] allow() default {};


  /**
   * After matching an allow rule, an incoming request that has at least one principal
   * that matches a deny rule will be denied.
   */
  Matcher[] deny() default {};


  /**
   * The status code to respond with when access is denied.
   * <p>
   * By default, this will be '403 Forbidden' for HTTP endpoints and 'PERMISSION DENIED (7)' for gRPC endpoints.
   * If set at class-level, it will automatically be inherited by all methods in the class that are not
   * annotated with their own @Acl definition.
   *
   * For HTTP, common used values are between 400 and 599,
   * see exhaustive list at https://www.rfc-editor.org/rfc/rfc9110.html#name-status-codes
   *
   * For gRPC, the status codes values can be consulted at https://grpc.github.io/grpc/core/md_doc_statuscodes.html
   *
   */
  int denyCode() default -1;

  /**
   * A principal matcher that can be used in an ACL.
   * <p>
   * A principal is a very broad concept. It can correlate to a person, a system, or a more abstract concept, such as
   * the internet.
   * <p>
   * A single request may have multiple principals associated with it, for example, it may have come from a particular
   * source system, and it may have certain credentials associated with it. When a matcher is applied to the request,
   * the request is considered to match if at least one of the principals attached to the request matches.
   * <p>
   * Each Matcher can be configured either with a 'service' or a 'principal', but not both.
   */
  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface Matcher {

    /**
     * Match a service principal.
     * <p>
     * This matches a service in the same project.
     * <p>
     * Supports glob matching, that is, * means all services in this project.
     */
    String service() default "";

    /** A principal matcher that can be specified with no additional configuration. */
    Principal principal() default Principal.UNSPECIFIED;
  }


  /**
   * This enum contains principal matchers that don't have any configuration, such as a name, associated with them,
   * for ease of reference in annotations.
   */
  enum Principal {
    UNSPECIFIED,
    /**
     * All (or no) principals. This matches all requests regardless of what principals are
     * associated with it.
     */
    ALL,
    /**
     * The internet. This will match all requests that originated from the internet, and passed
     * through the ingress via a configured route.
     */
    INTERNET
  }
}
