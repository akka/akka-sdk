/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.tooling.processor;

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
import javax.lang.model.type.TypeKind;
import javax.tools.*;
import java.io.*;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;


@SupportedAnnotationTypes(
        {
                "kalix.javasdk.annotations.http.Endpoint",
                // all entities will have this
                "kalix.javasdk.annotations.TypeId",
                // views
                "kalix.javasdk.annotations.ViewId",
                // actions
                "kalix.javasdk.annotations.ActionId",
                // actions or views
                "kalix.javasdk.annotations.Consume.FromValueEntity",
                "kalix.javasdk.annotations.Consume.FromEventSourcedEntity",
                "kalix.javasdk.annotations.Consume.FromTopic",
                "kalix.javasdk.annotations.Consume.FromServiceStream",
                // central config/lifecycle class
                "kalix.javasdk.annotations.KalixService"
        })
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ComponentAnnotationProcessor extends AbstractProcessor {

    private static final String COMPONENT_DESCRIPTOR_FILE_PATH = "META-INF/kalix-components.conf";

    // parent path in hoconf
    private static final String DESCRIPTOR_ENTRY_BASE_PATH = "kalix.jvm.sdk.";
    private static final String DESCRIPTOR_COMPONENT_ENTRY_BASE_PATH = DESCRIPTOR_ENTRY_BASE_PATH + "components.";

    // key of each component type under that parent path, containing a string list of concrete component classes
    private static final String ENDPOINT_KEY = "endpoint";
    private static final String EVENT_SOURCED_ENTITY_KEY = "event-sourced-entity";
    private static final String VALUE_ENTITY_KEY = "value-entity";
    private static final String ACTION_KEY = "action";
    private static final String VIEW_KEY = "view";
    private static final String WORKFLOW_KEY = "workflow";
    private static final String KALIX_SERVICE_KEY = "kalix-service";


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
                var elementsPerComponentType = annotatedElements.stream().filter(element -> {
                    if (element.getKind().isClass()) return true;
                    else {
                        warning("Kalix annotation processor filtering out " + element.getSimpleName() +
                                " since it is not a class (even though it has annotation " + annotation + ")");
                        return false;
                    }
                }).collect(Collectors.groupingBy(element -> componentTypeFor(element, annotation)));
                elementsPerComponentType.forEach((componentType, elements) -> {
                    var classNames = new ArrayList<>(elements.stream().map(element -> ((TypeElement)element).getQualifiedName().toString()).toList());
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
            var actions = componentTypeToConcreteComponents.get(ACTION_KEY);
            if (actions != null) {
                var foundDuplicates = new HashSet<String>();
                actions.forEach(actionClass -> {
                    if (componentTypeToConcreteComponents.entrySet().stream().anyMatch((entry) -> !entry.getKey().equals(ACTION_KEY) && entry.getValue().contains(actionClass))) {
                        foundDuplicates.add(actionClass);
                    }
                });
                actions.removeAll(foundDuplicates);
                if (actions.isEmpty()) componentTypeToConcreteComponents.remove(ACTION_KEY);
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



            var service = componentTypeToConcreteComponents.get(KALIX_SERVICE_KEY);
            if (service != null && service.size() > 1) {
                error("More than one class annotated with @KalixService, only one is allowed. Annotated classes: " + String.join(", ", service));
            }

            // nested tables will occur together with the wrapping class, list only the wrapping class

            try {
                if (!componentTypeToConcreteComponents.isEmpty()) {
                    var summary = String.join(", ", componentTypeToConcreteComponents.entrySet().stream().map(mapEntry ->
                        mapEntry.getValue().size() + " " + mapEntry.getKey()
                    ).toList());
                    info("Kalix annotation processor detected components: " + summary);
                    createComponentServiceDescriptor(componentTypeToConcreteComponents);
                } else {
                    debug("Kalix annotation processor found no annotated components");
                }
            } catch (IOException ex) {
                error("Kalix annotation processor failed to create Kalix component descriptor: " + ex.getMessage());
                throw new RuntimeException(ex);
            }
            return true;
        } else {
            return false;
        }
    }

    private String componentTypeFor(Element annotatedClass, TypeElement annotation) {
        return switch (annotation.getQualifiedName().toString()) {
            case "kalix.javasdk.annotations.http.Endpoint" -> ENDPOINT_KEY;
            case "kalix.javasdk.annotations.ViewId" -> VIEW_KEY;
            case "kalix.javasdk.annotations.ActionId" -> ACTION_KEY;
            case "kalix.javasdk.annotations.Consume" -> ACTION_KEY;
            case "kalix.javasdk.annotations.KalixService" -> KALIX_SERVICE_KEY;
            case "kalix.javasdk.annotations.TypeId" -> entityComponentType(annotatedClass);
            case String s when s.startsWith("kalix.javasdk.annotations.Consume") ->
                    actionOrView(annotatedClass);
            default -> throw new IllegalArgumentException("Unknown annotation type: " + annotation.getQualifiedName());
        };
    }

    private String actionOrView(Element annotatedClass) {
        var superClassMirror = ((TypeElement) annotatedClass).getSuperclass();
        if (superClassMirror.getKind() != TypeKind.NONE && superClassMirror.toString().equals("kalix.javasdk.action.Action")) {
            return ACTION_KEY;
        } else {
            // no superclass, or superclass but that is not Action
            return VIEW_KEY;
        }
    }

    /**
     * Entities share the same annotation, so we need to look at class supertype
     */
    private String entityComponentType(Element annotatedClass) {
        var superClassMirror = ((TypeElement) annotatedClass).getSuperclass();
        var superClassName = superClassMirror.toString();

        // cut out type params if any
        int typeParams = superClassName.indexOf("<");
        if (typeParams > -1) {
            superClassName = superClassName.substring(0, typeParams);
        }

        debug("Determining entity component type trough supertype: " + superClassName);
        return switch (superClassName) {
            case "kalix.javasdk.eventsourcedentity.EventSourcedEntity" -> EVENT_SOURCED_ENTITY_KEY;
            case "kalix.javasdk.valueentity.ValueEntity" -> VALUE_ENTITY_KEY;
            case "kalix.javasdk.workflow.Workflow" -> WORKFLOW_KEY;
            default -> throw new IllegalArgumentException("Unknown supertype for class [" + annotatedClass + "] annotated with @TypeId: [" + superClassName + "]");
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
        componentTypeToConcreteComponents.forEach((componentType, foundComponentClasses) -> {
            Set<String> components = new HashSet<>();
            if (componentType.equals(KALIX_SERVICE_KEY)) {
                // only one kalix service annotated class
                if (!foundComponentClasses.isEmpty())
                    config.put(DESCRIPTOR_ENTRY_BASE_PATH + KALIX_SERVICE_KEY, foundComponentClasses.getFirst());
            } else {
                // all components can be multiple
                var componentTypeConfigPath = DESCRIPTOR_COMPONENT_ENTRY_BASE_PATH + componentType;
                if (foundExistingConfig.hasPath(componentTypeConfigPath))
                    components.addAll(foundExistingConfig.getStringList(componentTypeConfigPath));
                if (components.addAll(foundComponentClasses)) {
                    config.put(componentTypeConfigPath, components.stream().toList());
                }
            }
        });

        var newDescriptorResource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", COMPONENT_DESCRIPTOR_FILE_PATH);
        debug("Kalix annotation processor writing component descriptor " + new File(newDescriptorResource.toUri()));
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
