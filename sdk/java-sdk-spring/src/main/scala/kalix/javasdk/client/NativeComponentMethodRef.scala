/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client

import akka.NotUsed
import akka.annotation.DoNotInherit
import kalix.javasdk.DeferredCall
import kalix.javasdk.Metadata

import java.util.concurrent.CompletionStage

/**
 * No argument component call representation, not executed until invoked or by some mechanism using the deferred call
 * (like a timer executing it later for example)
 *
 * Not for user extension
 */
@DoNotInherit
trait NativeComponentMethodRef[R] {
  def withMetadata(metadata: Metadata): NativeComponentMethodRef[R]
  def deferred(): DeferredCall[NotUsed, R]
  def invokeAsync(): CompletionStage[R]
}

/**
 * One argument component call representation, not executed until invoked or by some mechanism using the deferred call
 * (like a timer executing it later for example)
 *
 * @tparam R
 *   the return type of the call
 * @tparam A1
 *   the argument type of the call
 *
 * Not for user extension
 */
@DoNotInherit
trait NativeComponentMethodRef1[A1, R] {
  def withMetadata(metadata: Metadata): NativeComponentMethodRef1[A1, R]
  def deferred(arg: A1): DeferredCall[A1, R]
  def invokeAsync(arg: A1): CompletionStage[R]
}

/**
 * No argument component call representation, not executed until invoked. Used for component methods that cannot be
 * deferred.
 *
 * Not for user extension
 */
@DoNotInherit
trait NativeComponentInvokeOnlyMethodRef[R] {
  def withMetadata(metadata: Metadata): NativeComponentMethodRef[R]
  def invokeAsync(): CompletionStage[R]
}

/**
 * One argument component call representation, not executed until invoked Used for component methods that cannot be
 * deferred.
 *
 * @tparam R
 *   the return type of the call
 * @tparam A1
 *   the argument type of the call
 *
 * Not for user extension
 */
@DoNotInherit
trait NativeComponentInvokeOnlyMethodRef1[A1, R] {
  def withMetadata(metadata: Metadata): NativeComponentMethodRef1[A1, R]
  def invokeAsync(arg: A1): CompletionStage[R]
}
