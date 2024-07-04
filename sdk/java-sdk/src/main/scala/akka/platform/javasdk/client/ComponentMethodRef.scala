/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.client

import akka.NotUsed
import akka.annotation.DoNotInherit
import akka.platform.javasdk.DeferredCall
import akka.platform.javasdk.Metadata

import java.util.concurrent.CompletionStage

/**
 * No argument component call representation, not executed until invoked or by some mechanism using the deferred call
 * (like a timer executing it later for example)
 *
 * Not for user extension
 */
@DoNotInherit
trait ComponentMethodRef[R] {
  def withMetadata(metadata: Metadata): ComponentMethodRef[R]
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
trait ComponentMethodRef1[A1, R] {
  def withMetadata(metadata: Metadata): ComponentMethodRef1[A1, R]
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
trait ComponentInvokeOnlyMethodRef[R] {
  def withMetadata(metadata: Metadata): ComponentMethodRef[R]
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
trait ComponentInvokeOnlyMethodRef1[A1, R] {
  def withMetadata(metadata: Metadata): ComponentMethodRef1[A1, R]
  def invokeAsync(arg: A1): CompletionStage[R]
}
