/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

/**
 * A tool call the model requested within a model response, together with its recorded response.
 *
 * @param id the id of the tool call, correlating the request with its response
 * @param name the name of the tool that was called
 * @param arguments the arguments passed to the tool, as a JSON string
 * @param response the tool's response, as a string
 */
public record ToolCall(String id, String name, String arguments, String response) {}
