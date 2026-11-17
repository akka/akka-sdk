/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import com.typesafe.config.Config;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.StandardLocation;

/**
 * Writes {@code META-INF/akka-javasdk-inventory_<groupId>_<artifactId>.json}, listing the
 * components, endpoints and endpoint routes in the sources of the compile and the guardrails that
 * {@code application.conf} enables. Writes nothing when the compile has no component or endpoint,
 * or when an annotation processor reports an error in the same compile.
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

  private static final String SDK_DEFAULTS_PACKAGE = "akka.javasdk";
  private static final String SDK_DEFAULTS_FILE = "governance.conf";
  private static final String SDK_DEFAULTS_PATH =
      SDK_DEFAULTS_PACKAGE.replace('.', '/') + "/" + SDK_DEFAULTS_FILE;
  private static final String APPLICATION_CONF = "application.conf";

  private static final String COMPONENT_ANNOTATION = "akka.javasdk.annotations.Component";
  private static final String AGENT_ROLE_ANNOTATION = "akka.javasdk.annotations.AgentRole";

  private final List<InventoryComponent> components = new ArrayList<>();
  private final List<InventoryRoute> routes = new ArrayList<>();

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
    var inventoryRoutes = new InventoryRoutes(processingEnv);

    for (TypeElement annotation : annotations) {
      for (TypeElement type :
          ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(annotation))) {
        var kind = ComponentKinds.kindFor(type, annotation);
        if (kind.equals(ComponentKinds.SERVICE_SETUP)) {
          continue;
        }

        var className = processingEnv.getElementUtils().getBinaryName(type).toString();
        var component = AnnotationValues.find(type, COMPONENT_ANNOTATION);

        Optional<String> role = Optional.empty();
        if (kind.equals(ComponentKinds.AGENT) || kind.equals(ComponentKinds.AUTONOMOUS_AGENT)) {
          role =
              AnnotationValues.find(type, AGENT_ROLE_ANNOTATION)
                  .flatMap(m -> AnnotationValues.string(m, "value"));
        }

        components.add(
            new InventoryComponent(
                kind,
                component.flatMap(m -> AnnotationValues.string(m, "id")),
                className,
                component.flatMap(m -> AnnotationValues.string(m, "name")),
                component.flatMap(m -> AnnotationValues.string(m, "description")),
                role));

        routes.addAll(inventoryRoutes.routesOf(type, kind));
      }
    }
  }

  private void writeInventory() {
    var path = INVENTORY_FILE_PREFIX + groupId + "_" + artifactId + INVENTORY_FILE_SUFFIX;
    var componentLibrary =
        Boolean.parseBoolean(processingEnv.getOptions().get(OPTION_COMPONENT_LIBRARY));

    List<InventoryGuardrail> guardrails;
    try {
      var config = InventoryConfig.merge(readApplicationConf(), readSdkDefaults());
      guardrails = GuardrailReader.read(config, this::warning);
    } catch (InventoryException ex) {
      error(ex.getMessage());
      return;
    }

    var inventory =
        new Inventory(groupId, artifactId, componentLibrary, components, routes, guardrails);

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

  private Config readSdkDefaults() throws InventoryException {
    try {
      var resource =
          processingEnv
              .getFiler()
              .getResource(StandardLocation.CLASS_PATH, SDK_DEFAULTS_PACKAGE, SDK_DEFAULTS_FILE);
      try (var reader = resource.openReader(true)) {
        return InventoryConfig.parse(
            reader, SDK_DEFAULTS_PATH, include -> warnSkippedInclude(SDK_DEFAULTS_PATH, include));
      }
    } catch (NoSuchFileException | FileNotFoundException ex) {
      throw new InventoryException(
          "Akka SDK annotation processor cannot find "
              + SDK_DEFAULTS_PATH
              + " on the compile classpath",
          ex);
    } catch (IOException ex) {
      throw new InventoryException(
          "Akka SDK annotation processor cannot read " + SDK_DEFAULTS_PATH + ": " + ex.getMessage(),
          ex);
    }
  }

  private Optional<Config> readApplicationConf() throws InventoryException {
    try {
      var resource = existingOutputResource(APPLICATION_CONF);
      if (resource.isEmpty()) {
        debug("No application.conf found for the inventory");
        return Optional.empty();
      }
      return Optional.of(
          InventoryConfig.parse(
              resource.get().toUri().toURL(),
              include -> warnSkippedInclude(APPLICATION_CONF, include)));
    } catch (IOException ex) {
      throw new InventoryException(
          "Akka SDK annotation processor cannot read " + APPLICATION_CONF + ": " + ex.getMessage(),
          ex);
    }
  }

  private void warnSkippedInclude(String file, String include) {
    warning(
        "The service inventory does not evaluate include "
            + include
            + " in "
            + file
            + ". Define the guardrails in "
            + APPLICATION_CONF
            + ".");
  }
}
