/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import java.util.Optional;

/**
 * The experiment trial an evaluation's subject came out of — one run of one dataset item — present
 * on {@link EvaluationContext#experiment()} when the evaluation was triggered as part of a running
 * experiment.
 *
 * @param experimentId the id of the experiment this evaluation belongs to
 * @param datasetId the id of the dataset the experiment is running against
 * @param datasetItemId the id of the dataset item that produced this evaluation's subject
 * @param agentRepetition which repetition of the agent run produced this evaluation's subject
 * @param judgeRepetition which repetition of the judge run this evaluation is
 * @param expectedOutput the dataset item's expected output, where the dataset provides one
 */
public record ExperimentContext(
    String experimentId,
    String datasetId,
    String datasetItemId,
    int agentRepetition,
    int judgeRepetition,
    // TODO: replace Object with the dataset item's ExpectedOutput type once #5560 lands.
    Optional<Object> expectedOutput) {}
