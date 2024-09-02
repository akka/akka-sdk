/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
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
   *
   * By default, this will be 'Forbidden', but alternatives might include 'Authentication required' or 'Not
   * Found'.
   *
   */
  DenyStatusCode denyCode() default DenyStatusCode.FORBIDDEN;

  /**
   * If `true`, indicates that the {@code denyCode} should be inherited from the parent.
   * If set to `true` in the top most parent - like the `Main` class - then it will be equivalent to set {@code denyCode} to 'FORBIDDEN'
   * @return
   */
  boolean inheritDenyCode() default false;

  enum DenyStatusCode {
    BAD_REQUEST(3),
    FORBIDDEN(7),
    NOT_FOUND(5),
    AUTHENTICATION_REQUIRED(16),
    CONFLICT(6),
    INTERNAL_SERVER_ERROR(13),
    SERVICE_UNAVAILABLE(14),
    GATEWAY_TIMEOUT(4);



    public final int value;
    DenyStatusCode(int value){
      this.value = value;
    }

  }


  /**
   * A principal matcher that can be used in an ACL.
   *
   * A principal is a very broad concept. It can correlate to a person, a system, or a more abstract concept, such as
   * the internet.
   *
   * A single request may have multiple principals associated with it, for example, it may have come from a particular
   * source system, and it may have certain credentials associated with it. When a matcher is applied to the request,
   * the request is considered to match if at least one of the principals attached to the request matches.
   *
   * Each Matcher can be configured either with a 'service' or a 'principal', but not both.
   */
  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface Matcher {

    /**
     * Match a service principal.
     *
     * This matches a service in the same project.
     *
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
