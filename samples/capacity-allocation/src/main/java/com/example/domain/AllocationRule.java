package com.example.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "ruleType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AllocationRule.MaxPerUserRule.class, name = "max-per-user")
})
public sealed interface AllocationRule {

  String ruleName();

  String description();

  /** Rule that limits the maximum number of capacity units that can be allocated per user. */
  @JsonTypeName("max-per-user")
  record MaxPerUserRule(String ruleName, int maxAllocation, String description)
      implements AllocationRule {
    public MaxPerUserRule {
      if (maxAllocation <= 0) {
        throw new IllegalArgumentException("Maximum allocation must be greater than zero");
      }
      if (description == null) {
        description = "Limits users to a maximum of " + maxAllocation + " allocated capacity units";
      }
    }
  }
}
