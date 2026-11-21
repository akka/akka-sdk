/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * A set of security schemes that a client must satisfy together, each with the scopes it needs. An
 * agent card lists alternatives as several requirements. The JSON form is {@code {"schemes":
 * {"google": {"list": ["openid"]}}}}.
 *
 * @param schemes the scheme names as declared in {@link A2aAgentCard#securitySchemes()}, each with
 *     its required scopes
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aSecurityRequirement(Map<String, Scopes> schemes) {

  public A2aSecurityRequirement {
    schemes = A2aValues.map(schemes);
  }

  /**
   * The scopes that a requirement needs from one scheme.
   *
   * @param list the scope names
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Scopes(List<String> list) {

    public Scopes {
      list = A2aValues.list(list);
    }
  }
}
