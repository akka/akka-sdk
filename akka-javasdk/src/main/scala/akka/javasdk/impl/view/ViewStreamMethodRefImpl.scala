/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import java.time.Instant

import akka.NotUsed
import akka.annotation.InternalApi
import akka.javasdk.client.ViewStreamMethodRef
import akka.javasdk.client.ViewStreamMethodRef1
import akka.javasdk.view.EntryWithMetadata
import akka.stream.javadsl.Source

/**
 * INTERNAL API
 */
@InternalApi
final class ViewStreamMethodRefImpl[R](invoke: Option[Instant] => Source[EntryWithMetadata[R], NotUsed])
    extends ViewStreamMethodRef[R] {
  override def source(): Source[R, NotUsed] = invoke(None).map(_.entry())
  override def entriesSource(): Source[EntryWithMetadata[R], NotUsed] = invoke(None)
  override def entriesSource(updatedAfter: Instant): Source[EntryWithMetadata[R], NotUsed] = invoke(Some(updatedAfter))
}

/**
 * INTERNAL API
 */
@InternalApi
final class ViewStreamMethodRefImpl1[A1, R](invoke: (A1, Option[Instant]) => Source[EntryWithMetadata[R], NotUsed])
    extends ViewStreamMethodRef1[A1, R] {
  override def source(arg: A1): Source[R, NotUsed] = invoke(arg, None).map(_.entry())
  override def entriesSource(arg: A1): Source[EntryWithMetadata[R], NotUsed] = invoke(arg, None)
  override def entriesSource(arg: A1, updatedAfter: Instant): Source[EntryWithMetadata[R], NotUsed] =
    invoke(arg, Some(updatedAfter))
}
