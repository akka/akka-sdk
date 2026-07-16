/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A classifier maps an input to a {@link Classification} — a score and/or label, with optional
 * confidence and metadata. Can be implemented with rules or regex, embeddings, traditional ML, a
 * small specialised model, a prompted or fine-tuned LLM, or an external classification API — the
 * implementation is just user code calling out to whatever it needs.
 *
 * <p>An implementation has a public constructor into which the platform's managed dependencies can
 * be injected — including a {@link ClassifierClient}, for composing other configured classifiers
 * (for example, an ensemble classifier combining several underlying ones), and a {@link
 * ClassifierContext} for the classifier's configured name and config section.
 *
 * <p>Unlike a {@link Guardrail}, a classifier is never bound to a call boundary and is never
 * dispatched by the runtime. It is invoked inline, wherever a guardrail, an evaluator, or
 * application code needs it, through an injected {@link ClassifierClient} by name.
 *
 * <p>Classifiers are enabled with configuration; see agent documentation.
 */
public interface Classifier {

  /**
   * Classifies {@code input} and returns the resulting {@link Classification}.
   *
   * <p>This is the method to implement. It may block: classifiers are always invoked on virtual
   * threads.
   *
   * @return the classification
   */
  Classification classify(String input);

  /**
   * Async variant of {@link #classify}, for implementations that prefer composing futures.
   *
   * <p>The default implementation delegates to {@link #classify}. When this method is overridden,
   * {@link #classify} is no longer used, and it is then safe to have it throw {@link
   * UnsupportedOperationException} or return {@code null}.
   *
   * @return a CompletionStage with the classification
   */
  default CompletionStage<Classification> classifyAsync(String input) {
    return CompletableFuture.completedFuture(classify(input));
  }
}
