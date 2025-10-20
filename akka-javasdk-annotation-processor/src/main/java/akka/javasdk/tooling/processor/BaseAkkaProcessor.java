/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.tools.Diagnostic;

public abstract class BaseAkkaProcessor extends AbstractProcessor {
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
}
