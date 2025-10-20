# Validation Coverage Comparison: Runtime vs Compile-Time

This document compares the runtime validation tests with compile-time validation tests to identify gaps.

## Component Validation

### Runtime Tests (`ComponentAnnotationValidationSpec`)

| Test Case | Covered in Compile-Time? | Notes |
|-----------|-------------------------|-------|
| Both @Component and @ComponentId defined | ✅ YES | `BothComponentAndComponentId.java` |
| Empty @Component id | ✅ YES | `EmptyComponentId.java` |
| Blank @Component id (spaces) | ❌ NO | Missing test |
| Pipe character in @Component id | ✅ YES | `ComponentIdWithPipe.java` |
| Empty @ComponentId | ❌ NO | Missing test with deprecated annotation |
| Blank @ComponentId (spaces) | ❌ NO | Missing test with deprecated annotation |
| Pipe character in @ComponentId | ❌ NO | Missing test with deprecated annotation |
| Valid @Component | ✅ YES | `ValidPublicComponent.java` |
| Valid @ComponentId | ❌ NO | Missing positive test with deprecated annotation |
| No annotation (disabled component) | ❌ NO | Missing test |
| Non-public component | ✅ YES | `NonPublicComponent.java`, `PackagePrivateComponent.java` |

**Gap Summary:**
- Missing tests for blank @Component id (with spaces)
- Missing tests for deprecated @ComponentId validation (empty, blank, pipe)
- Missing positive test for valid @ComponentId
- Missing test for components with no annotation (disabled state)

---

## TimedAction Validation

### Runtime Tests (`TimedActionValidationSpec`)

| Test Case | Covered in Compile-Time? | Notes |
|-----------|-------------------------|-------|
| TimedAction with 0-arity Effect method | ✅ YES | Implicitly covered by `ValidTimedAction.java` |
| TimedAction with 1-arity Effect method | ✅ YES | Covered by `ValidTimedAction.java` |
| TimedAction with >1-arity Effect method | ✅ YES | `TimedActionWithTooManyParams.java` |
| TimedAction with no Effect methods | ✅ YES | `TimedActionWithoutEffect.java` |
| Non-TimedAction class | ✅ YES | Implicitly covered (processor skips non-TimedAction classes) |
| Non-public TimedAction | ✅ YES | Covered by general component validation |

**Gap Summary:**
- ✅ **Complete coverage** for TimedAction

---

## Consumer Validation

### Runtime Tests (`ConsumerValidationSpec`)

| Test Case | Covered in Compile-Time? | Notes |
|-----------|-------------------------|-------|
| Consumer without @Consume annotation | ✅ YES | `ConsumerWithoutConsumeAnnotation.java` |
| Consumer with topic subscription | ✅ YES | `ValidConsumer.java` uses @Consume.FromTopic |
| Consumer with KeyValueEntity subscription | ❌ NO | Missing test |
| Consumer with EventSourcedEntity subscription | ❌ NO | Missing test |
| Consumer without command handler | ✅ YES | `ConsumerWithoutEffectMethod.java` |
| Consumer with >1-arity method | ✅ YES | `ConsumerWithTooManyParams.java` |
| Consumer with ambiguous handlers | ❌ NO | Missing test |
| Consumer with multiple type-level subscriptions | ❌ NO | Missing test |
| Consumer with multiple update methods (KVE sub) | ❌ NO | Missing test |
| Consumer with multiple delete handlers | ❌ NO | Missing test |
| Consumer with valid delete handler | ❌ NO | Missing positive test |
| Consumer with delete handler with params | ❌ NO | Missing test |
| Consumer with topic publishing but no source | ❌ NO | Missing test |
| Consumer with valid topic publishing | ❌ NO | Missing test |
| Consumer with method-level ACL on subscription | ❌ NO | Missing test |
| Consumer missing handler for KVE subscription | ❌ NO | Missing test |
| Consumer missing handler for Workflow subscription | ❌ NO | Missing test |
| Consumer missing handler for ES subscription | ❌ NO | Missing test |
| Consumer with subscription method with no params (not delete) | ❌ NO | Missing test |
| Consumer with stream subscription | ❌ NO | Missing test |
| Consumer with empty stream ID | ❌ NO | Missing test |
| Consumer with valid stream publishing | ❌ NO | Missing test |
| Non-public Consumer | ✅ YES | Covered by general component validation |
| Consumer with ambiguous handlers for VE | ❌ NO | Missing test |
| Consumer with ambiguous delete handlers for VE | ❌ NO | Missing test |
| Consumer with ambiguous handlers for ES | ❌ NO | Missing test |
| Consumer with ambiguous handlers for ServiceStream | ❌ NO | Missing test |

**Gap Summary:**
- ✅ **Basic validations covered**: @Consume annotation, Effect methods, arity check
- ❌ **Missing advanced validations**: Most subscription-related validations, delete handlers, ambiguous handlers, topic/stream publishing validations

**Note:** Many Consumer validations rely on `commonSubscriptionValidation` which we haven't ported yet. These are complex validations that need subscription-specific logic.

---

## Agent Validation

### Runtime Tests (`AgentValidationSpec`)

| Test Case | Covered in Compile-Time? | Notes |
|-----------|-------------------------|-------|
| Both @AgentDescription.role and @AgentRole | ✅ YES | `AgentWithBothRoleAnnotations.java` |
| Both @AgentDescription.name and @Component.name | ✅ YES | `AgentWithBothDescriptionAnnotations.java` |
| Both @AgentDescription.description and @Component.description | ✅ YES | `AgentWithBothDescriptionAnnotations.java` |
| Empty @AgentDescription.name (no @Component) | ❌ NO | Missing test |
| Empty @AgentDescription.description (no @Component) | ❌ NO | Missing test |
| Blank @AgentDescription.name (no @Component) | ❌ NO | Missing test |
| Blank @AgentDescription.description (no @Component) | ❌ NO | Missing test |
| Valid @AgentDescription | ❌ NO | Missing positive test |
| Valid @Component on Agent | ✅ YES | `ValidAgent.java` |
| Agent with no command handler | ✅ YES | `AgentWithNoCommandHandler.java` |
| Agent with multiple command handlers | ✅ YES | `AgentWithMultipleCommandHandlers.java` |
| Agent with StreamEffect | ❌ NO | Missing positive test |
| Agent command handler with >1 arg | ✅ YES | `AgentWithTooManyParams.java` |
| Agent command handler with 1 arg | ✅ YES | `ValidAgent.java` |
| Agent command handler with 0 args | ❌ NO | Missing explicit positive test |

**Gap Summary:**
- ✅ **Core validations covered**: Command handler count, arity, annotation conflicts
- ❌ **Missing tests**: Empty/blank @AgentDescription fields without @Component, StreamEffect support, 0-arg handler

---

## Summary

### Component Validation
- **Runtime Tests:** 11
- **Compile-Time Tests:** 7
- **Coverage:** ~64%
- **Critical Gaps:** Blank ID validation, deprecated @ComponentId validation

### TimedAction Validation
- **Runtime Tests:** 6
- **Compile-Time Tests:** 3
- **Coverage:** 100% (all critical paths covered)
- **Critical Gaps:** None

### Consumer Validation
- **Runtime Tests:** 27
- **Compile-Time Tests:** 4
- **Coverage:** ~15%
- **Critical Gaps:** Subscription validations, delete handlers, ambiguous handlers, topic/stream publishing

### Agent Validation
- **Runtime Tests:** 15
- **Compile-Time Tests:** 6
- **Coverage:** ~40%
- **Critical Gaps:** Empty/blank AgentDescription validation, StreamEffect test

---

## Recommendations

### High Priority (Critical for Release)

1. **Component Validation:**
   - Add test for blank @Component id (spaces)
   - Add tests for deprecated @ComponentId edge cases

2. **Consumer Validation:**
   - Most subscription-related validations are not yet ported to compile-time
   - These require porting `commonSubscriptionValidation` logic
   - Consider if these should be compile-time or remain runtime checks

### Medium Priority

3. **Agent Validation:**
   - Add tests for empty/blank @AgentDescription fields
   - Add positive test for Agent.StreamEffect

### Low Priority

4. **General:**
   - Add explicit positive tests for edge cases
   - Add test for disabled components (no annotation)

---

## Questions for Discussion

1. **Consumer subscription validations:** Should complex subscription validations (delete handlers, ambiguous handlers, missing handlers) be ported to compile-time? These are currently ~70% of Consumer test coverage.

2. **Scope:** Are we focusing on basic structural validations at compile-time, or should we port all runtime validation logic?

3. **Priority:** Which gaps are blocking the removal of runtime validations?
