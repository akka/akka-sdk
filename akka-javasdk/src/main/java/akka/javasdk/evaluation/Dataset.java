/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import java.util.List;

/**
 * The cases an offline evaluation runs over.
 *
 * @param id the id of the dataset
 * @param items the cases in the dataset
 */
public record Dataset(String id, List<DatasetItem> items) {}
