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
    private static final String EVENT_SOURCED_ENTITY_KEY = "event-sourced-entity";
    private static final String VALUE_ENTITY_KEY = "key-value-entity";
    private static final String TIMED_ACTION_KEY = "timed-action";
    private static final String CONSUMER_KEY = "consumer";
    private static final String VIEW_KEY = "view";
    private static final String WORKFLOW_KEY = "workflow";
    private static final String SERVICE_SETUP_KEY = "service-setup";

    private static final List<String> ALL_COMPONENT_TYPES = List.of(HTTP_ENDPOINT_KEY, EVENT_SOURCED_ENTITY_KEY, VALUE_ENTITY_KEY, TIMED_ACTION_KEY, CONSUMER_KEY, VIEW_KEY, WORKFLOW_KEY, SERVICE_SETUP_KEY);


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

            // Extra pass until we have removed the RequestMapping support, can be present on all types of components
            // so, it is not a water tight way to find actions. If something is listed as both an action and some other
            // component type, remove it from actions
            var actions = componentTypeToConcreteComponents.get(TIMED_ACTION_KEY);
            if (actions != null) {
                var foundDuplicates = new HashSet<String>();
                actions.forEach(actionClass -> {
                    if (componentTypeToConcreteComponents.entrySet().stream().anyMatch((entry) -> !entry.getKey().equals(TIMED_ACTION_KEY) && entry.getValue().contains(actionClass))) {
                        foundDuplicates.add(actionClass);
                    }
                });
                actions.removeAll(foundDuplicates);
                if (actions.isEmpty()) componentTypeToConcreteComponents.remove(TIMED_ACTION_KEY);
            }

            var views = componentTypeToConcreteComponents.get(VIEW_KEY);
            if (views != null) {
                var foundNestedViews = new HashSet<String>();
                views.forEach(viewClass -> {
                    // For multi table views each table is an inner class to a view,
                    // we'll find both the parent wrapping class and the nested, but, we want list
                    // only the wrapping class as a component.
                    if(views.stream().anyMatch(otherViewClass ->
                            !otherViewClass.equals(viewClass) && viewClass.startsWith(otherViewClass) && viewClass.length() > otherViewClass.length()))
                        foundNestedViews.add(viewClass);
                });
                views.removeAll(foundNestedViews);
                if (views.isEmpty()) componentTypeToConcreteComponents.remove(VIEW_KEY);
            }



            var service = componentTypeToConcreteComponents.get(SERVICE_SETUP_KEY);
            if (service != null && service.size() > 1) {
                error("More than one class annotated with @Setup, only one is allowed. Annotated classes: " + String.join(", ", service));
            }

            // nested tables will occur together with the wrapping class, list only the wrapping class

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
            case "akka.javasdk.annotations.Setup" -> SERVICE_SETUP_KEY;
            case "akka.javasdk.annotations.ComponentId" -> componentType(annotatedClass);
            default -> throw new IllegalArgumentException("Unknown annotation type: " + annotation.getQualifiedName());
        };
    }

    /**
     * A multi-view is a regular class with nested classes implementing View.
     * This method returns true if at least one inner class implements View
     */
    private boolean isMultiView(Element annotatedClass) {

        var innerClasses =
          annotatedClass.getEnclosedElements().stream()
            .filter(e -> e instanceof TypeElement)
            .map(this::superClassName)
            .toList();

        // is one of the inner classes is a View, we have a MultiView
        return innerClasses.contains("akka.javasdk.view.View");
    }

    // extract the super class name of the passed Element (without the type parameter brackets)
    private String superClassName(Element annotatedClass) {
        var superClassMirror = ((TypeElement) annotatedClass).getSuperclass();
        var superClassName = superClassMirror.toString();

        // cut out type params if any
        int typeParams = superClassName.indexOf("<");
        if (typeParams > -1) {
            return superClassName.substring(0, typeParams);
        } else {
            return superClassName;
        }
    }

    /**
     * Entities share the same annotation, so we need to look at class supertype
     */
    private String componentType(Element annotatedClass) {

        var superClassName = superClassName(annotatedClass);

        debug("Determining component type trough supertype: " + superClassName);
        return switch (superClassName) {
            case "akka.javasdk.eventsourcedentity.EventSourcedEntity" -> EVENT_SOURCED_ENTITY_KEY;
            case "akka.javasdk.keyvalueentity.KeyValueEntity" -> VALUE_ENTITY_KEY;
            case "akka.javasdk.workflow.Workflow" -> WORKFLOW_KEY;
            case "akka.javasdk.timedaction.TimedAction" -> TIMED_ACTION_KEY;
            case "akka.javasdk.consumer.Consumer" -> CONSUMER_KEY;
            case "akka.javasdk.view.View" -> VIEW_KEY;
            default -> {
                if (isMultiView(annotatedClass)) yield VIEW_KEY;
                else
                    throw new IllegalArgumentException("Unknown supertype for class [" + annotatedClass + "] annotated with @ComponentId: [" + superClassName + "]");
            }
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
