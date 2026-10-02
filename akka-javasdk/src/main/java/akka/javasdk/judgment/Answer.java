/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.judgment;

/**
 * The answer to one {@link Question}. The type of the answer follows the type of the question. Read
 * an answer with {@link Judgment#answer(Question)}.
 */
public sealed interface Answer permits ChoiceAnswer, ScoreAnswer, YesNoAnswer {}
