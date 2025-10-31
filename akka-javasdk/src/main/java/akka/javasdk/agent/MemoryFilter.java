/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MemoryFilter.IncludeFromAgentId.class, name = "include-from-id"),
  @JsonSubTypes.Type(value = MemoryFilter.ExcludeFromAgentId.class, name = "exclude-from-id"),
  @JsonSubTypes.Type(value = MemoryFilter.IncludeFromAgentRole.class, name = "include-from-role"),
  @JsonSubTypes.Type(value = MemoryFilter.ExcludeFromAgentRole.class, name = "exclude-from-role")
})
public sealed interface MemoryFilter {
  record IncludeFromAgentId(Set<String> ids) implements MemoryFilter {}

  record ExcludeFromAgentId(Set<String> ids) implements MemoryFilter {}

  record IncludeFromAgentRole(Set<String> roles) implements MemoryFilter {}

  record ExcludeFromAgentRole(Set<String> roles) implements MemoryFilter {}

  static MemoryFilter includeFromAgentId(String id) {
    return new IncludeFromAgentId(Set.of(id));
  }

  static MemoryFilter includeFromAgentId(Set<String> ids) {
    return new IncludeFromAgentId(ids);
  }

  static MemoryFilter excludeFromAgentId(String id) {
    return new ExcludeFromAgentId(Set.of(id));
  }

  static MemoryFilter excludeFromAgentId(Set<String> ids) {
    return new ExcludeFromAgentId(ids);
  }

  static MemoryFilter includeFromAgentRole(String role) {
    return new IncludeFromAgentRole(Set.of(role));
  }

  static MemoryFilter includeFromAgentRole(Set<String> roles) {
    return new IncludeFromAgentRole(roles);
  }

  static MemoryFilter excludeFromAgentRole(String role) {
    return new ExcludeFromAgentRole(Set.of(role));
  }

  static MemoryFilter excludeFromAgentRole(Set<String> roles) {
    return new ExcludeFromAgentRole(roles);
  }
}
