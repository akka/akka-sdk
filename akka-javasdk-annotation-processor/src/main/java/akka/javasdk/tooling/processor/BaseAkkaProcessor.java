/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import javax.annotation.processing.AbstractProcessor;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

public abstract class BaseAkkaProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  private final boolean debugEnabled;

  protected BaseAkkaProcessor() {
    // can be passed to compiler: `mvn compile -Dakka-component-processor.debug=true`
    debugEnabled = Boolean.getBoolean("akka-component-processor.debug");
  }

  protected void debug(Object msg) {
    if (debugEnabled)
      processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg.toString());
  }

  protected void info(Object msg) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg.toString());
  }

  protected void warning(Object msg) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg.toString());
  }

  protected void error(Object msg) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg.toString());
  }

  /** Returns the class output file at {@code path}, or empty when the file does not exist. */
  protected Optional<FileObject> existingOutputResource(String path) throws IOException {
    try {
      var resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", path);
      try (var in = resource.openInputStream()) {
        return Optional.of(resource);
      }
    } catch (NoSuchFileException | FileNotFoundException ex) {
      // javac throws NoSuchFileException. IntelliJ's JPS throws FileNotFoundException.
      // ECJ throws FileNotFoundException from getResource.
      return Optional.empty();
    }
  }
}
