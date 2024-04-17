package org.example.service

import com.google.protobuf.Descriptors
import kalix.scalasdk.action.ActionCreationContext
import kalix.scalasdk.action.ActionOptions
import kalix.scalasdk.action.ActionProvider

import scala.collection.immutable.Seq

// This code is managed by Kalix tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

object MyServiceActionProvider {
  def apply(actionFactory: ActionCreationContext => MyServiceActionImpl): MyServiceActionProvider =
    new MyServiceActionProvider(actionFactory, ActionOptions.defaults)

  def apply(actionFactory: ActionCreationContext => MyServiceActionImpl, options: ActionOptions): MyServiceActionProvider =
    new MyServiceActionProvider(actionFactory, options)
}

class MyServiceActionProvider private(actionFactory: ActionCreationContext => MyServiceActionImpl,
                                      override val options: ActionOptions)
  extends ActionProvider[MyServiceActionImpl] {

  override final def serviceDescriptor: Descriptors.ServiceDescriptor =
    ExampleActionProto.javaDescriptor.findServiceByName("MyServiceAction")

  override final def newRouter(context: ActionCreationContext): MyServiceActionRouter =
    new MyServiceActionRouter(actionFactory(context))

  override final def additionalDescriptors: Seq[Descriptors.FileDescriptor] =
    ExampleActionProto.javaDescriptor ::
    Nil

  def withOptions(options: ActionOptions): MyServiceActionProvider =
    new MyServiceActionProvider(actionFactory, options)
}

