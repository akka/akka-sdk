# Component Annotation Migration Guide

## Overview

The Akka Java SDK now supports a new `@Component` annotation that provides enhanced metadata capabilities compared to the legacy `@ComponentId` annotation. The new annotation includes:

- `id` (mandatory): The unique component identifier
- `name` (optional): A human-readable name for the component  
- `description` (optional): A description of what the component does

## Backward Compatibility

The legacy `@ComponentId` annotation is still supported and will continue to work. When both annotations are present, the new `@Component` annotation takes precedence.

## Examples

### Using the new @Component annotation

```java
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(
    id = "user-registry",
    name = "User Registry", 
    description = "Manages user information and profiles"
)
public class UserRegistry extends KeyValueEntity<User> {
    // Implementation...
}
```

### Legacy @ComponentId annotation (still supported)

```java
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@ComponentId("user-registry")
public class UserRegistry extends KeyValueEntity<User> {
    // Implementation...
}
```

## Migration Recommendations

1. **Gradual Migration**: You can migrate components one by one from `@ComponentId` to `@Component`
2. **Enhanced Documentation**: Use the `name` and `description` fields to better document your components
3. **Tooling Support**: The new annotation provides better IDE support and documentation generation

## Utility Methods

The TestKit provides utility methods to access component metadata:

```java
import akka.javasdk.testkit.TestKit;

// Get component ID (works with both annotations)
String id = TestKit.getComponentIdValue(MyComponent.class);

// Get component name (only available with @Component)
String name = TestKit.getComponentName(MyComponent.class);

// Get component description (only available with @Component)  
String description = TestKit.getComponentDescription(MyComponent.class);
```

## Notes

- The `@ComponentId` annotation is now marked as `@Deprecated` but will continue to be supported
- Component IDs should remain stable for entities, workflows, and views as they affect storage representation
- The pipe character '|' is not allowed in component IDs
- Component IDs must be non-empty strings