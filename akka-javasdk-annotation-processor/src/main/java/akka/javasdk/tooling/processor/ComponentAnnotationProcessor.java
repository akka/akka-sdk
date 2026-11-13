/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes({
  "akka.javasdk.annotations.http.HttpEndpoint",
  "akka.javasdk.annotations.GrpcEndpoint",
  "akka.javasdk.annotations.mcp.McpEndpoint",
  // all components will have this
  "akka.javasdk.annotations.Component",
  // central config/lifecycle class
  "akka.javasdk.annotations.Setup"
})
public class ComponentAnnotationProcessor extends BaseAkkaProcessor {

  // Compiler options for artifact coordinates (passed via -A options)
  private static final String OPTION_GROUP_ID = "akka.javasdk.groupId";
  private static final String OPTION_ARTIFACT_ID = "akka.javasdk.artifactId";
  // "true" when compiling a component library rather than a deployable service
  private static final String OPTION_COMPONENT_LIBRARY = "akka.javasdk.componentLibrary";

  // Base path for descriptor files - artifact coordinates are appended
  private static final String COMPONENT_DESCRIPTOR_FILE_PREFIX =
      "META-INF/akka-javasdk-components_";
  private static final String COMPONENT_DESCRIPTOR_FILE_SUFFIX = ".conf";

  // parent path in hoconf
  private static final String DESCRIPTOR_ENTRY_BASE_PATH = "akka.javasdk.";
  private static final String DESCRIPTOR_COMPONENT_ENTRY_BASE_PATH =
      DESCRIPTOR_ENTRY_BASE_PATH + "components.";

  // Marks the descriptor of the deployable service, must be kept in sync with
  // ComponentLocator.scala
  private static final String SERVICE_MARKER_PATH = DESCRIPTOR_ENTRY_BASE_PATH + "service";

  // Descriptor key of each component type. Each key under the components path holds a string list
  // of concrete component classes.
  private static final List<String> ALL_COMPONENT_TYPES =
      List.of(
          ComponentKinds.HTTP_ENDPOINT,
          ComponentKinds.GRPC_ENDPOINT,
          ComponentKinds.MCP_ENDPOINT,
          ComponentKinds.EVENT_SOURCED_ENTITY,
          ComponentKinds.KEY_VALUE_ENTITY,
          ComponentKinds.TIMED_ACTION,
          ComponentKinds.CONSUMER,
          ComponentKinds.VIEW,
          ComponentKinds.WORKFLOW,
          ComponentKinds.AGENT,
          ComponentKinds.AUTONOMOUS_AGENT,
          ComponentKinds.EVALUATOR,
          ComponentKinds.DURABLE_EVALUATOR,
          ComponentKinds.SERVICE_SETUP);

  private boolean alreadyRan = false;

  @Override
  public Set<String> getSupportedOptions() {
    return Set.of(OPTION_GROUP_ID, OPTION_ARTIFACT_ID, OPTION_COMPONENT_LIBRARY);
  }

  private String getDescriptorFilePath() {
    var options = processingEnv.getOptions();
    var groupId = options.get(OPTION_GROUP_ID);
    var artifactId = options.get(OPTION_ARTIFACT_ID);

    if (groupId == null || groupId.isEmpty()) {
      throw new IllegalStateException(
          "Compiler option '"
              + OPTION_GROUP_ID
              + "' is required but was not provided. "
              + "Ensure your pom.xml configures the maven-compiler-plugin with "
              + "-A"
              + OPTION_GROUP_ID
              + "=<groupId>");
    }
    if (artifactId == null || artifactId.isEmpty()) {
      throw new IllegalStateException(
          "Compiler option '"
              + OPTION_ARTIFACT_ID
              + "' is required but was not provided. "
              + "Ensure your pom.xml configures the maven-compiler-plugin with "
              + "-A"
              + OPTION_ARTIFACT_ID
              + "=<artifactId>");
    }

    return COMPONENT_DESCRIPTOR_FILE_PREFIX
        + groupId
        + "_"
        + artifactId
        + COMPONENT_DESCRIPTOR_FILE_SUFFIX;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (!alreadyRan) {
      // processor may be invoked several times - only run this on the first iteration (or else we
      // get errors
      // from trying to open the descriptor files multiple times)
      alreadyRan = true;

      Map<String, List<String>> componentTypeToConcreteComponents = new HashMap<>();
      for (TypeElement annotation : annotations) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
        var elementsPerComponentType =
            ElementFilter.typesIn(annotatedElements).stream()
                .collect(
                    Collectors.groupingBy(element -> ComponentKinds.kindFor(element, annotation)));
        elementsPerComponentType.forEach(
            (componentType, elements) -> {
              var classNames =
                  new ArrayList<>(
                      elements.stream()
                          .map(
                              element ->
                                  processingEnv
                                      .getElementUtils()
                                      .getBinaryName((TypeElement) element)
                                      .toString())
                          .toList());
              if (componentTypeToConcreteComponents.containsKey(componentType)) {
                // the same component might have multiple annotations, deduplication happens when
                // creating config later
                classNames.addAll(componentTypeToConcreteComponents.get(componentType));
              }
              debug(
                  "Found "
                      + classNames.size()
                      + " components of type "
                      + componentType
                      + " annotated with "
                      + annotation
                      + ": "
                      + String.join(", ", classNames));
              componentTypeToConcreteComponents.put(componentType, classNames);
            });
      }

      var service = componentTypeToConcreteComponents.get(ComponentKinds.SERVICE_SETUP);
      if (service != null && service.size() > 1) {
        error(
            "More than one class annotated with @Setup, only one is allowed. Annotated classes: "
                + String.join(", ", service));
      }

      try {
        if (!componentTypeToConcreteComponents.isEmpty()) {
          var summary =
              String.join(
                  ", ",
                  componentTypeToConcreteComponents.entrySet().stream()
                      .map(mapEntry -> mapEntry.getValue().size() + " " + mapEntry.getKey())
                      .toList());
          info("Akka SDK annotation processor detected components: " + summary);
          createComponentServiceDescriptor(componentTypeToConcreteComponents);
        } else {
          debug("Akka SDK annotation processor found no annotated components");
        }
      } catch (IOException ex) {
        error(
            "Akka SDK annotation processor failed to create component descriptor: "
                + ex.getMessage());
        throw new RuntimeException(ex);
      }
      return false;
    } else {
      return false;
    }
  }

  private void createComponentServiceDescriptor(
      Map<String, List<String>> componentTypeToConcreteComponents) throws IOException {
    var filer = processingEnv.getFiler();
    var descriptorFilePath = getDescriptorFilePath();

    Config existingConfig;
    try {
      var existingDescriptorResource =
          filer.getResource(StandardLocation.CLASS_OUTPUT, "", descriptorFilePath);

      try (var in = existingDescriptorResource.openReader(true)) {
        // this could be both user defined existing config, and a previous compile without clean
        // inbetween
        debug("Existing Akka component descriptor found, will merge with discovered components");
        try (in) {
          existingConfig = ConfigFactory.parseReader(in);
        }
      }
      existingDescriptorResource.delete();
    } catch (NoSuchFileException | FileNotFoundException ex) {
      // No existing descriptor — expected on a clean build or first compile in IntelliJ.
      // Standard javac (Maven/Gradle) throws NoSuchFileException via PathFileObject.openReader(),
      // while IntelliJ's JPS build system uses JpsFileObject/OutputFileObject backed by
      // FileInputStream, which throws FileNotFoundException instead.
      debug("No existing Akka component descriptor found");
      existingConfig = ConfigFactory.empty();
    }

    final Config foundExistingConfig = existingConfig;
    final Map<String, Object> config = new HashMap<>();
    ALL_COMPONENT_TYPES.forEach(
        componentType -> {
          var foundComponentClasses =
              componentTypeToConcreteComponents.getOrDefault(componentType, List.of());
          if (componentType.equals(ComponentKinds.SERVICE_SETUP)) {
            // only one Akka service annotated class
            String serviceSetupPath = DESCRIPTOR_ENTRY_BASE_PATH + ComponentKinds.SERVICE_SETUP;
            if (foundComponentClasses.isEmpty()) {
              if (foundExistingConfig.hasPath(serviceSetupPath)) {
                // use the old value
                config.put(serviceSetupPath, foundExistingConfig.getString(serviceSetupPath));
              }
            } else {
              config.put(serviceSetupPath, foundComponentClasses.getFirst());
            }
          } else {
            Set<String> components = new HashSet<>();
            var componentTypeConfigPath = DESCRIPTOR_COMPONENT_ENTRY_BASE_PATH + componentType;
            // add existing ones
            if (foundExistingConfig.hasPath(componentTypeConfigPath))
              components.addAll(foundExistingConfig.getStringList(componentTypeConfigPath));
            // add new ones (could be empty)
            components.addAll(foundComponentClasses);
            if (!components.isEmpty()) {
              debug(
                  "Adding "
                      + components.size()
                      + " components of type "
                      + componentType
                      + ": "
                      + String.join(", ", components)
                      + " to descriptor");
              config.put(componentTypeConfigPath, components.stream().toList());
            }
          }
        });

    // always from the current option, never from the existing descriptor
    if (!Boolean.parseBoolean(processingEnv.getOptions().get(OPTION_COMPONENT_LIBRARY))) {
      config.put(SERVICE_MARKER_PATH, true);
    }

    var newDescriptorResource =
        filer.createResource(StandardLocation.CLASS_OUTPUT, "", descriptorFilePath);
    debug(
        "Akka SDK annotation processor writing component descriptor "
            + new File(newDescriptorResource.toUri()));
    writeConfig(newDescriptorResource, ConfigFactory.parseMap(config));
  }

  private void writeConfig(FileObject descriptorResource, Config config) throws IOException {
    try (Writer out = descriptorResource.openWriter()) {
      var writer = new BufferedWriter(out);
      var configAsString =
          config.root().render(ConfigRenderOptions.concise().setFormatted(true).setJson(false));
      writer.write(configAsString);
      writer.flush();
    }
  }
}
