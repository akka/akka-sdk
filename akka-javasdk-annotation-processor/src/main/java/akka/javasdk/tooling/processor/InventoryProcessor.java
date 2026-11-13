/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.StandardLocation;

/**
 * Writes {@code META-INF/akka-javasdk-inventory_<groupId>_<artifactId>.json}, listing the
 * components and endpoints in the sources of the compile. Writes nothing when the compile has no
 * component or endpoint, or when an annotation processor reports an error in the same compile.
 */
@SupportedAnnotationTypes({
  "akka.javasdk.annotations.Component",
  "akka.javasdk.annotations.http.HttpEndpoint",
  "akka.javasdk.annotations.GrpcEndpoint",
  "akka.javasdk.annotations.mcp.McpEndpoint",
  "akka.javasdk.annotations.Setup"
})
public class InventoryProcessor extends BaseAkkaProcessor {

  private static final String OPTION_GROUP_ID = "akka.javasdk.groupId";
  private static final String OPTION_ARTIFACT_ID = "akka.javasdk.artifactId";
  private static final String OPTION_COMPONENT_LIBRARY = "akka.javasdk.componentLibrary";

  private static final String INVENTORY_FILE_PREFIX = "META-INF/akka-javasdk-inventory_";
  private static final String INVENTORY_FILE_SUFFIX = ".json";

  private static final String COMPONENT_ANNOTATION = "akka.javasdk.annotations.Component";
  private static final String AGENT_ROLE_ANNOTATION = "akka.javasdk.annotations.AgentRole";

  private final List<InventoryComponent> components = new ArrayList<>();

  private boolean alreadyRan = false;
  private String groupId;
  private String artifactId;

  @Override
  public Set<String> getSupportedOptions() {
    return Set.of(OPTION_GROUP_ID, OPTION_ARTIFACT_ID, OPTION_COMPONENT_LIBRARY);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (!alreadyRan) {
      alreadyRan = true;
      groupId = requiredOption(OPTION_GROUP_ID);
      artifactId = requiredOption(OPTION_ARTIFACT_ID);
      if (groupId != null && artifactId != null) {
        collectComponents(annotations, roundEnv);
      }
    }

    if (roundEnv.processingOver() && !components.isEmpty() && !roundEnv.errorRaised()) {
      writeInventory();
    }

    return true;
  }

  private String requiredOption(String name) {
    var value = processingEnv.getOptions().get(name);
    if (value == null || value.isEmpty()) {
      error("Compiler option '" + name + "' is required but was not provided.");
      return null;
    }
    return value;
  }

  private void collectComponents(
      Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (TypeElement type :
          ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(annotation))) {
        var kind = ComponentKinds.kindFor(type, annotation);
        if (kind.equals(ComponentKinds.SERVICE_SETUP)) {
          continue;
        }

        var className = processingEnv.getElementUtils().getBinaryName(type).toString();
        var component = findAnnotation(type, COMPONENT_ANNOTATION);

        Optional<String> role = Optional.empty();
        if (kind.equals(ComponentKinds.AGENT) || kind.equals(ComponentKinds.AUTONOMOUS_AGENT)) {
          role =
              findAnnotation(type, AGENT_ROLE_ANNOTATION).flatMap(m -> explicitValue(m, "value"));
        }

        components.add(
            new InventoryComponent(
                kind,
                component.flatMap(m -> explicitValue(m, "id")),
                className,
                component.flatMap(m -> explicitValue(m, "name")),
                component.flatMap(m -> explicitValue(m, "description")),
                role));
      }
    }
  }

  private void writeInventory() {
    var path = INVENTORY_FILE_PREFIX + groupId + "_" + artifactId + INVENTORY_FILE_SUFFIX;
    var componentLibrary =
        Boolean.parseBoolean(processingEnv.getOptions().get(OPTION_COMPONENT_LIBRARY));
    var inventory = new Inventory(groupId, artifactId, componentLibrary, components);

    try {
      var resource =
          processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", path);
      try (var out = resource.openOutputStream()) {
        out.write(InventoryJson.render(inventory).getBytes(StandardCharsets.UTF_8));
      }
      debug("Akka SDK annotation processor wrote inventory " + path);
    } catch (IOException ex) {
      error(
          "Akka SDK annotation processor failed to write inventory "
              + path
              + ": "
              + ex.getMessage());
    }
  }

  private static Optional<AnnotationMirror> findAnnotation(
      TypeElement type, String annotationName) {
    for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
      var annotationType = (TypeElement) mirror.getAnnotationType().asElement();
      if (annotationType.getQualifiedName().contentEquals(annotationName)) {
        return Optional.of(mirror);
      }
    }
    return Optional.empty();
  }

  /** Returns the explicit, non-empty string value of an annotation element. */
  private static Optional<String> explicitValue(AnnotationMirror mirror, String elementName) {
    return mirror.getElementValues().entrySet().stream()
        .filter(entry -> entry.getKey().getSimpleName().contentEquals(elementName))
        .map(entry -> entry.getValue().getValue())
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(value -> !value.isEmpty())
        .findFirst();
  }
}
