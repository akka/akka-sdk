/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedWriter;
import java.io.File;
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


@SupportedAnnotationTypes(
        {
                "akka.javasdk.annotations.http.HttpEndpoint",
                "akka.javasdk.annotations.GrpcEndpoint",
                // all components will have this
                "akka.javasdk.annotations.ComponentId",
                // central config/lifecycle class
                "akka.javasdk.annotations.Setup"
        })
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ComponentAnnotationProcessor extends AbstractProcessor {

    private static final String COMPONENT_DESCRIPTOR_FILE_PATH = "META-INF/akka-javasdk-components.conf";

    // parent path in hoconf
    private static final String DESCRIPTOR_ENTRY_BASE_PATH = "akka.javasdk.";
    private static final String DESCRIPTOR_COMPONENT_ENTRY_BASE_PATH = DESCRIPTOR_ENTRY_BASE_PATH + "components.";

    // key of each component type under that parent path, containing a string list of concrete component classes
    private static final String HTTP_ENDPOINT_KEY = "http-endpoint";
    private static final String GRPC_ENDPOINT_KEY = "grpc-endpoint";
    private static final String EVENT_SOURCED_ENTITY_KEY = "event-sourced-entity";
    private static final String VALUE_ENTITY_KEY = "key-value-entity";
    private static final String TIMED_ACTION_KEY = "timed-action";
    private static final String CONSUMER_KEY = "consumer";
    private static final String VIEW_KEY = "view";
    private static final String WORKFLOW_KEY = "workflow";
    private static final String CHAT_AGENT_KEY = "chat-agent";
    private static final String SERVICE_SETUP_KEY = "service-setup";

    private static final List<String> ALL_COMPONENT_TYPES = List.of(HTTP_ENDPOINT_KEY, GRPC_ENDPOINT_KEY,
        EVENT_SOURCED_ENTITY_KEY, VALUE_ENTITY_KEY, TIMED_ACTION_KEY, CONSUMER_KEY, VIEW_KEY, WORKFLOW_KEY,
        CHAT_AGENT_KEY, SERVICE_SETUP_KEY);


    private final boolean debugEnabled;
    private boolean alreadyRan = false;

    public ComponentAnnotationProcessor() {
        // can be passed to compiler: `mvn compile -Dakka-component-processor.debug=true`
        debugEnabled = Boolean.getBoolean("akka-component-processor.debug");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!alreadyRan) {
            // processor may be invoked several times - only run this on the first iteration (or else we get errors
            // from trying to open the descriptor files multiple times)
            alreadyRan = true;

            Map<String, List<String>> componentTypeToConcreteComponents = new HashMap<>();
            for (TypeElement annotation : annotations) {
                Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
                var elementsPerComponentType = ElementFilter.typesIn(annotatedElements)
                  .stream()
                  .collect(Collectors.groupingBy(element -> componentTypeFor(element, annotation)));
                elementsPerComponentType.forEach((componentType, elements) -> {
                    var classNames = new ArrayList<>(elements.stream().map(element -> element.getQualifiedName().toString()).toList());
                    if (componentTypeToConcreteComponents.containsKey(componentType)) {
                        // the same component might have multiple annotations, deduplication happens when creating config later
                        classNames.addAll(componentTypeToConcreteComponents.get(componentType));
                    }
                    debug("Found "  + classNames.size() + " components of type " + componentType + " annotated with " + annotation + ": " + String.join(", ", classNames));
                    componentTypeToConcreteComponents.put(componentType, classNames);
                });
            }

            var service = componentTypeToConcreteComponents.get(SERVICE_SETUP_KEY);
            if (service != null && service.size() > 1) {
                error("More than one class annotated with @Setup, only one is allowed. Annotated classes: " + String.join(", ", service));
            }

            try {
                if (!componentTypeToConcreteComponents.isEmpty()) {
                    var summary = String.join(", ", componentTypeToConcreteComponents.entrySet().stream().map(mapEntry ->
                        mapEntry.getValue().size() + " " + mapEntry.getKey()
                    ).toList());
                    info("Akka SDK annotation processor detected components: " + summary);
                    createComponentServiceDescriptor(componentTypeToConcreteComponents);
                } else {
                    debug("Akka SDK annotation processor found no annotated components");
                }
            } catch (IOException ex) {
                error("Akka SDK annotation processor failed to create component descriptor: " + ex.getMessage());
                throw new RuntimeException(ex);
            }
            return true;
        } else {
            return false;
        }
    }

    private String componentTypeFor(Element annotatedClass, TypeElement annotation) {
        return switch (annotation.getQualifiedName().toString()) {
            case "akka.javasdk.annotations.http.HttpEndpoint" -> HTTP_ENDPOINT_KEY;
            case "akka.javasdk.annotations.GrpcEndpoint" -> GRPC_ENDPOINT_KEY;
            case "akka.javasdk.annotations.Setup" -> SERVICE_SETUP_KEY;
            case "akka.javasdk.annotations.ComponentId" -> componentType(annotatedClass);
            default -> throw new IllegalArgumentException("Unknown annotation type: " + annotation.getQualifiedName());
        };
    }

    /**
     * Entities share the same annotation, so we need to look at class supertypes
     */
    private String componentType(Element annotatedClass) {
        return recurseForComponentType(annotatedClass, annotatedClass);
    }

    private String recurseForComponentType(Element annotatedClass, Element current) {
        var superClassTypeMirror = ((TypeElement) current).getSuperclass();
        var superClassElement = ((DeclaredType) superClassTypeMirror).asElement();

        var superClassName = superClassTypeMirror.toString();

        // cut out type params if any
        int typeParams = superClassName.indexOf("<");
        var classNameWithoutTypeParams = typeParams > 0 ? superClassName.substring(0, typeParams) : superClassName;
        debug("Determining component type trough supertype: " +  classNameWithoutTypeParams);

        return switch (classNameWithoutTypeParams) {
            case "akka.javasdk.eventsourcedentity.EventSourcedEntity" -> EVENT_SOURCED_ENTITY_KEY;
            case "akka.javasdk.keyvalueentity.KeyValueEntity" -> VALUE_ENTITY_KEY;
            case "akka.javasdk.workflow.Workflow" -> WORKFLOW_KEY;
            case "akka.javasdk.timedaction.TimedAction" -> TIMED_ACTION_KEY;
            case "akka.javasdk.consumer.Consumer" -> CONSUMER_KEY;
            case "akka.javasdk.view.View" -> VIEW_KEY;
            case "akka.javasdk.agent.ChatAgent" -> CHAT_AGENT_KEY;
            case "java.lang.Object" -> throw new IllegalArgumentException("Unknown supertype for class [" + annotatedClass + "] annotated with @ComponentId: [" + superClassName + "]");
            default ->
                // go through hierarchy
                recurseForComponentType(annotatedClass, superClassElement);
        };
    }

    private void createComponentServiceDescriptor(Map<String, List<String>> componentTypeToConcreteComponents) throws IOException {
        var filer = processingEnv.getFiler();

        Config existingConfig;
        try {
            var existingDescriptorResource = filer.getResource(StandardLocation.CLASS_OUTPUT, "", COMPONENT_DESCRIPTOR_FILE_PATH);

            try(var in = existingDescriptorResource.openReader(true)) {
                // this could be both user defined existing config, and a previous compile without clean inbetween
                debug("Existing kalix component descriptor found, will merge with discovered components");
                try (in) {
                    existingConfig = ConfigFactory.parseReader(in);
                }
            }
            existingDescriptorResource.delete();
        } catch (NoSuchFileException ex) {
            // no existing file
            debug("No existing kalix component descriptor found");
            existingConfig = ConfigFactory.empty();
        }

        final Config foundExistingConfig = existingConfig;
        final Map<String, Object> config = new HashMap<>();
        ALL_COMPONENT_TYPES.forEach(componentType -> {
            var foundComponentClasses = componentTypeToConcreteComponents.getOrDefault(componentType, List.of());
            if (componentType.equals(SERVICE_SETUP_KEY)) {
                // only one kalix service annotated class
                String serviceSetupPath = DESCRIPTOR_ENTRY_BASE_PATH + SERVICE_SETUP_KEY;
                if (foundComponentClasses.isEmpty()) {
                    if (foundExistingConfig.hasPath(serviceSetupPath)) {
                        //use the old value
                        config.put(serviceSetupPath, foundExistingConfig.getString(serviceSetupPath));
                    }
                } else {
                    config.put(serviceSetupPath, foundComponentClasses.getFirst());
                }
            } else {
                Set<String> components = new HashSet<>();
                var componentTypeConfigPath = DESCRIPTOR_COMPONENT_ENTRY_BASE_PATH + componentType;
                //add existing ones
                if (foundExistingConfig.hasPath(componentTypeConfigPath))
                    components.addAll(foundExistingConfig.getStringList(componentTypeConfigPath));
                //add new ones (could be empty)
                components.addAll(foundComponentClasses);
                if (!components.isEmpty()) {
                    debug("Adding " + components.size() + " components of type " + componentType +  ": " + String.join(", ", components) + " to descriptor");
                    config.put(componentTypeConfigPath, components.stream().toList());
                }
            }
        });

        var newDescriptorResource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", COMPONENT_DESCRIPTOR_FILE_PATH);
        debug("Akka SDK annotation processor writing component descriptor " + new File(newDescriptorResource.toUri()));
        writeConfig(newDescriptorResource, ConfigFactory.parseMap(config));
    }

    private void writeConfig(FileObject descriptorResource, Config config) throws IOException {
        try (Writer out = descriptorResource.openWriter()) {
            var writer = new BufferedWriter(out);
            var configAsString = config.root().render(
                    ConfigRenderOptions.concise()
                            .setFormatted(true)
                            .setJson(false));
            writer.write(configAsString);
            writer.flush();
        }
    }

    private void debug(Object msg) {
        if (debugEnabled)
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg.toString());
    }

    private void info(Object msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg.toString());
    }

    private void warning(Object msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg.toString());
    }

    private void error(Object msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg.toString());
    }
}
