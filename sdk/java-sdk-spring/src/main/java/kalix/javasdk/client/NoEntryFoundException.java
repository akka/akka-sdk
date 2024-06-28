/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client;

/** Thrown for a query where the query result would be a single entry but none was found. */
// FIXME this matches what we have now but is still not great DX, Option or always a collection
// would be better?
public final class NoEntryFoundException extends RuntimeException {

    public NoEntryFoundException(String message) {
        super(message);
    }
}
