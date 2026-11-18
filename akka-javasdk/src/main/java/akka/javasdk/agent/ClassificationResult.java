/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Optional;

/**
 * The result of a {@link ClassifierClient} call: the classification and the id of its record in the
 * ledger.
 *
 * @param classificationId the id of the record of the classification in the ledger, or empty when
 *     the ledger is disabled. Pass it to {@link
 *     akka.javasdk.ledger.LedgerClient#getClassification(String)} to fetch the record.
 * @param classification the classification that the classifier produced
 */
public record ClassificationResult(
    Optional<String> classificationId, Classification classification) {}
