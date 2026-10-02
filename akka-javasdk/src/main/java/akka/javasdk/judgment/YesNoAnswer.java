/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.judgment;

/**
 * The answer to a {@link Question.YesNo}. Compare the probability with a threshold that fits the
 * cost of a wrong answer in your use case.
 *
 * @param probability the probability that the answer is yes, from 0 to 1
 */
public record YesNoAnswer(double probability) implements Answer {}
