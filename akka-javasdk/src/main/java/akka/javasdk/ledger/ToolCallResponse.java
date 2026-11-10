/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import akka.javasdk.agent.MessageContent;
import java.util.List;

/**
 * A tool call response that arrived as input to a model call, typically a capability-tool
 * resolution from a prior iteration. The model saw these as input.
 *
 * @param id the id of the tool call this is a response to
 * @param name the name of the tool that produced the response
 * @param contents the content of the response, as pieces of {@link MessageContent}. A piece of a
 *     kind that this version of the SDK or the runtime does not know is left out.
 */
public record ToolCallResponse(String id, String name, List<MessageContent> contents) {}
