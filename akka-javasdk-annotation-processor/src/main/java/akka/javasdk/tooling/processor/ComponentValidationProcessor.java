/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import akka.javasdk.tooling.validation.Validation;
import akka.javasdk.tooling.validation.Validations;
import akka.javasdk.validation.ast.compiletime.CompileTimeTypeDef;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

/**
 * Annotation processor that performs compile-time validation on classes annotated
 * with @Component, @HttpEndpoint, @GrpcEndpoint, or @McpEndpoint.
 */
@SupportedAnnotationTypes({
  "akka.javasdk.annotations.Component",
  "akka.javasdk.annotations.http.HttpEndpoint",
  "akka.javasdk.annotations.GrpcEndpoint",
  "akka.javasdk.annotations.mcp.McpEndpoint"
})
public class ComponentValidationProcessor extends BaseAkkaProcessor {

  private static final String HTTP_ENDPOINT_ANNOTATION =
      "akka.javasdk.annotations.http.HttpEndpoint";

  private static final String COMPONENT_ANNOTATION = "akka.javasdk.annotations.Component";

  private static final String APPLICATION_CONF = "application.conf";

  private static final String ALLOW_DUPLICATE_COMPONENT_IDS_KEY =
      "akka.javasdk.allow-duplicate-component-ids";

  // Classes annotated with @Component in all rounds, keyed by qualified name.
  private final Map<String, TypeElement> componentsByName = new HashMap<>();

  // Qualified names of the classes already reported with a duplicate id.
  private final Set<String> reportedDuplicates = new HashSet<>();

  // Value of akka.javasdk.allow-duplicate-component-ids for this compilation.
  private boolean allowDuplicateComponentIds;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    allowDuplicateComponentIds = readAllowDuplicateComponentIds();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    // Collect all validation errors before reporting them
    java.util.List<ValidationError> allErrors = new java.util.ArrayList<>();

    info("Validating Akka components...");
    for (TypeElement annotation : annotations) {
      var annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);

      for (TypeElement element : ElementFilter.typesIn(annotatedElements)) {
        debug("Validating " + element.getSimpleName());

        if (annotation.getQualifiedName().contentEquals(COMPONENT_ANNOTATION)) {
          componentsByName.put(element.getQualifiedName().toString(), element);
        }

        // Wrap TypeElement in CompileTimeTypeDef
        CompileTimeTypeDef typeDef = new CompileTimeTypeDef(element);

        Validation validation = Validations.validate(typeDef);

        if (validation instanceof Validation.Invalid(java.util.List<String> errorMessages)) {
          debug("Component " + element.getSimpleName() + " is invalid");
          // Collect errors instead of reporting immediately
          for (String message : errorMessages) {
            allErrors.add(new ValidationError(element, message));
          }
        } else {
          debug("Component " + element.getSimpleName() + " is valid");
        }
      }
    }

    // Check id uniqueness over the components of all rounds, reporting each class once
    List<CompileTimeTypeDef> componentTypeDefs =
        componentsByName.values().stream().map(CompileTimeTypeDef::new).toList();

    Validations.duplicateComponentIdErrors(componentTypeDefs, allowDuplicateComponentIds)
        .forEach(
            (className, message) -> {
              if (reportedDuplicates.add(className)) {
                allErrors.add(new ValidationError(componentsByName.get(className), message));
              }
            });

    // Report all errors at once
    if (!allErrors.isEmpty()) {
      info("Found " + allErrors.size() + " validation error(s):");
      for (ValidationError error : allErrors) {
        processingEnv
            .getMessager()
            .printMessage(Diagnostic.Kind.ERROR, error.message, error.element);
      }
    }

    // if empty, we should return false to allow other processors to process these annotations
    // otherwise we can shortcut it because the build will fail anyway
    return !allErrors.isEmpty();
  }

  // Reads the setting from system properties, then from application.conf in the class output.
  private boolean readAllowDuplicateComponentIds() {
    Config config =
        ConfigFactory.systemProperties()
            .withFallback(readApplicationConf())
            .resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true));

    return config.hasPath(ALLOW_DUPLICATE_COMPONENT_IDS_KEY)
        && config.getBoolean(ALLOW_DUPLICATE_COMPONENT_IDS_KEY);
  }

  // Parses application.conf from the class output, resolving relative includes next to it.
  private Config readApplicationConf() {
    try {
      var applicationConfResource =
          processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", APPLICATION_CONF);

      return ConfigFactory.parseURL(
          applicationConfResource.toUri().toURL(),
          ConfigParseOptions.defaults().setAllowMissing(true));
    } catch (FileNotFoundException ex) {
      debug("No application.conf found in the class output");
      return ConfigFactory.empty();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    } catch (ConfigException ex) {
      warning(
          "Failed to parse application.conf in the class output. The processor reads "
              + ALLOW_DUPLICATE_COMPONENT_IDS_KEY
              + " from system properties only. Cause: "
              + ex.getMessage());
      return ConfigFactory.empty();
    }
  }

  // Helper class to store validation errors
  private record ValidationError(TypeElement element, String message) {}
}
