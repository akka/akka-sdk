/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import org.junit.jupiter.api.Test;

public class ComponentAnnotationTest {

  @Component(
      id = "new-component",
      name = "Test Component",
      description = "A test component using the new annotation")
  static class NewComponentEntity extends KeyValueEntity<String> {}

  @ComponentId("old-component")
  static class OldComponentEntity extends KeyValueEntity<String> {}

  static class NoAnnotationEntity extends KeyValueEntity<String> {}

  @Test
  public void testNewComponentAnnotation() {
    String componentId = ComponentDescriptorFactory.readComponentIdValue(NewComponentEntity.class);
    assertEquals("new-component", componentId);

    var name = ComponentDescriptorFactory.readComponentName(NewComponentEntity.class);
    assertTrue(name.isDefined());
    assertEquals("Test Component", name.get());

    var description = ComponentDescriptorFactory.readComponentDescription(NewComponentEntity.class);
    assertTrue(description.isDefined());
    assertEquals("A test component using the new annotation", description.get());
  }

  @Test
  public void testOldComponentIdAnnotation() {
    String componentId = ComponentDescriptorFactory.readComponentIdValue(OldComponentEntity.class);
    assertEquals("old-component", componentId);

    var name = ComponentDescriptorFactory.readComponentName(OldComponentEntity.class);
    assertFalse(name.isDefined());

    var description = ComponentDescriptorFactory.readComponentDescription(OldComponentEntity.class);
    assertFalse(description.isDefined());
  }

  @Test
  public void testMissingAnnotationThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          ComponentDescriptorFactory.readComponentIdValue(NoAnnotationEntity.class);
        });
  }
}
