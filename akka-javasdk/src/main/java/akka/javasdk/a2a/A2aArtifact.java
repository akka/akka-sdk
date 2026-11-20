/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * An output of a task, for example a document or an image.
 *
 * @param artifactId the id of the artifact, unique within its task
 * @param name a short name
 * @param description a longer description
 * @param parts the content
 * @param metadata free form details about the artifact
 * @param extensions URIs of the protocol extensions that apply to the artifact
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aArtifact(
    String artifactId,
    String name,
    String description,
    List<A2aPart> parts,
    Map<String, Object> metadata,
    List<String> extensions) {

  public A2aArtifact {
    parts = A2aValues.list(parts);
    metadata = A2aValues.optionalMap(metadata);
    extensions = A2aValues.list(extensions);
  }
}
