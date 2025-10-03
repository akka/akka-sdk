/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

import scala.annotation.nowarn

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.annotations.Acl
import akka.javasdk.annotations.Component
import akka.javasdk.annotations.ComponentId
import akka.javasdk.annotations.Consume.FromEventSourcedEntity
import akka.javasdk.annotations.Consume.FromKeyValueEntity
import akka.javasdk.annotations.Consume.FromServiceStream
import akka.javasdk.annotations.Consume.FromTopic
import akka.javasdk.annotations.Consume.FromWorkflow
import akka.javasdk.annotations.DeleteHandler
import akka.javasdk.annotations.Produce.ServiceStream
import akka.javasdk.annotations.Produce.ToTopic
import akka.javasdk.consumer.Consumer
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.agent.AgentDescriptorFactory
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.reflection.Reflect.Syntax._
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.view.TableUpdater
import akka.javasdk.view.View
import akka.javasdk.workflow.Workflow
import akka.runtime.sdk.spi.ConsumerDestination
import akka.runtime.sdk.spi.ConsumerSource

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ComponentDescriptorFactory {

  def hasAcl(javaMethod: Method): Boolean =
    javaMethod.isPublic && javaMethod.hasAnnotation[Acl]

  def hasKeyValueEntitySubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromKeyValueEntity]

  def hasWorkflowSubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromWorkflow]

  def hasEventSourcedEntitySubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromEventSourcedEntity]

  def streamSubscription(clazz: Class[_]): Option[FromServiceStream] =
    clazz.annotationOption[FromServiceStream]

  def hasSubscription(clazz: Class[_]): Boolean = {
    hasKeyValueEntitySubscription(clazz) ||
    hasWorkflowSubscription(clazz) ||
    hasEventSourcedEntitySubscription(clazz) ||
    hasTopicSubscription(clazz) ||
    hasStreamSubscription(clazz)
  }

  def eventSourcedEntitySubscription(clazz: Class[_]): Option[FromEventSourcedEntity] =
    clazz.annotationOption[FromEventSourcedEntity]

  def hasConsumerOutput(javaMethod: Method): Boolean = {
    if (javaMethod.isPublic) {
      javaMethod.getReturnType.isAssignableFrom(classOf[Consumer.Effect])
    } else {
      false
    }
  }

  def hasQueryEffectOutput(javaMethod: Method): Boolean = {
    javaMethod.isPublic && javaMethod.getReturnType == classOf[View.QueryEffect[_]]
  }

  def hasESEffectOutput(javaMethod: Method): Boolean = {
    javaMethod.isPublic &&
    (javaMethod.getReturnType == classOf[EventSourcedEntity.Effect[_]]
    || javaMethod.getReturnType == classOf[EventSourcedEntity.ReadOnlyEffect[_]])
  }

  def hasWorkflowEffectOutput(javaMethod: Method): Boolean = {
    javaMethod.isPublic &&
    (javaMethod.getReturnType == classOf[Workflow.Effect[_]]
    || javaMethod.getReturnType == classOf[Workflow.ReadOnlyEffect[_]])
  }

  def hasKVEEffectOutput(javaMethod: Method): Boolean = {
    javaMethod.isPublic &&
    (javaMethod.getReturnType == classOf[KeyValueEntity.Effect[_]]
    || javaMethod.getReturnType == classOf[KeyValueEntity.ReadOnlyEffect[_]])
  }

  def hasAgentEffectOutput(javaMethod: Method): Boolean = {
    javaMethod.isPublic &&
    (javaMethod.getReturnType == classOf[Agent.Effect[_]] ||
    javaMethod.getReturnType == classOf[Agent.StreamEffect])
  }

  def hasTimedActionEffectOutput(javaMethod: Method): Boolean = {
    javaMethod.isPublic && javaMethod.getReturnType == classOf[TimedAction.Effect]
  }

  def hasUpdateEffectOutput(javaMethod: Method): Boolean = {
    if (javaMethod.isPublic) {
      javaMethod.getGenericReturnType match {
        case p: ParameterizedType => p.getRawType.equals(classOf[TableUpdater.Effect[_]])
        case _                    => false
      }
    } else {
      false
    }
  }

  def hasHandleDeletes(javaMethod: Method): Boolean = {
    val ann = javaMethod.getAnnotation(classOf[DeleteHandler])
    javaMethod.isPublic && ann != null
  }

  def hasTopicSubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromTopic]

  def hasStreamSubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromServiceStream]

  def hasTopicPublication(clazz: Class[_]): Boolean =
    clazz.hasAnnotation[ToTopic]

  def hasStreamPublication(clazz: Class[_]): Boolean =
    clazz.hasAnnotation[ServiceStream]

  @nowarn("cat=deprecation")
  def readComponentIdValue(annotated: AnnotatedElement): String = {
    // First check for the new Component annotation
    val componentAnnotation = annotated.getAnnotation(classOf[Component])
    if (componentAnnotation ne null) {
      componentAnnotation.id()
    } else {
      // Fallback to the old ComponentId annotation for backward compatibility
      val componentIdAnnotation = annotated.getAnnotation(classOf[ComponentId])
      if (componentIdAnnotation eq null)
        throw new IllegalArgumentException(
          s"Component [$annotated] is missing ${classOf[Component].getName} or ${classOf[ComponentId].getName} annotation")
      else componentIdAnnotation.value()
    }
  }

  def readComponentName(annotated: AnnotatedElement): Option[String] = {
    val componentAnnotation = annotated.getAnnotation(classOf[Component])
    if (componentAnnotation ne null) {
      val name = componentAnnotation.name()
      if (name.nonEmpty) Some(name) else None
    } else {
      None
    }
  }

  def readComponentDescription(annotated: AnnotatedElement): Option[String] = {
    val componentAnnotation = annotated.getAnnotation(classOf[Component])
    if (componentAnnotation ne null) {
      val description = componentAnnotation.description()
      if (description.nonEmpty) Some(description) else None
    } else {
      None
    }
  }

  def findEventSourcedEntityClass(javaMethod: Method): Class[_ <: EventSourcedEntity[_, _]] = {
    val ann = javaMethod.getAnnotation(classOf[FromEventSourcedEntity])
    ann.value()
  }

  def findKVEClass(clazz: Class[_]): Class[_ <: KeyValueEntity[_]] = {
    val ann = clazz.getAnnotation(classOf[FromKeyValueEntity])
    ann.value()
  }

  def findWorkflowClass(clazz: Class[_]): Class[_ <: Workflow[_]] = {
    val ann = clazz.getAnnotation(classOf[FromWorkflow])
    ann.value()
  }

  def findSubscriptionEventSourcedComponentId(clazz: Class[_]): String = {
    val ann = clazz.getAnnotation(classOf[FromEventSourcedEntity])
    readComponentIdValue(ann.value())
  }

  def findSubscriptionWorkflowComponentId(clazz: Class[_]): String = {
    val ann = clazz.getAnnotation(classOf[FromWorkflow])
    readComponentIdValue(ann.value())
  }

  def findSubscriptionKeyValueEntityComponentId(component: Class[_]): String = {
    val ann = component.getAnnotation(classOf[FromKeyValueEntity])
    readComponentIdValue(ann.value())
  }

  def findHandleDeletes(component: Class[_]): Boolean = {
    component.getMethods.exists(hasHandleDeletes)
  }

  def findSubscriptionTopicName(javaMethod: Method): String = {
    val ann = javaMethod.getAnnotation(classOf[FromTopic])
    ann.value()
  }

  def findSubscriptionTopicName(clazz: Class[_]): String = {
    val ann = clazz.getAnnotation(classOf[FromTopic])
    ann.value()
  }

  def findSubscriptionConsumerGroup(javaMethod: Method): String = {
    val ann = javaMethod.getAnnotation(classOf[FromTopic])
    ann.consumerGroup()
  }

  private def findSubscriptionConsumerGroup(clazz: Class[_]): String = {
    val ann = clazz.getAnnotation(classOf[FromTopic])
    ann.consumerGroup()
  }

  def findPublicationTopicName(clazz: Class[_]): String = {
    val ann = clazz.getAnnotation(classOf[ToTopic])
    ann.value()
  }

  def hasIgnoreForTopic(clazz: Class[_]): Boolean = {
    val ann = clazz.getAnnotation(classOf[FromTopic])
    ann.ignoreUnknown()
  }

  def hasIgnoreForEventSourcedEntity(clazz: Class[_]): Boolean = {
    val ann = clazz.getAnnotation(classOf[FromEventSourcedEntity])
    ann.ignoreUnknown()
  }

  def findIgnore(clazz: Class[_]): Boolean = {
    if (hasTopicSubscription(clazz)) hasIgnoreForTopic(clazz)
    else if (hasEventSourcedEntitySubscription(clazz)) hasIgnoreForEventSourcedEntity(clazz)
    else false
  }

  def consumerSource(clazz: Class[_]): ConsumerSource = {
    if (hasKeyValueEntitySubscription(clazz)) {
      val kveComponentId = findSubscriptionKeyValueEntityComponentId(clazz)
      new ConsumerSource.KeyValueEntitySource(kveComponentId)
    } else if (hasWorkflowSubscription(clazz)) {
      val workflowComponentId = findSubscriptionWorkflowComponentId(clazz)
      new ConsumerSource.WorkflowSource(workflowComponentId)
    } else if (hasEventSourcedEntitySubscription(clazz)) {
      val esComponentId = findSubscriptionEventSourcedComponentId(clazz)
      new ConsumerSource.EventSourcedEntitySource(esComponentId)
    } else if (hasTopicSubscription(clazz)) {
      val topicName = findSubscriptionTopicName(clazz)
      val consumerGroup = findSubscriptionConsumerGroup(clazz)
      new ConsumerSource.TopicSource(topicName, consumerGroup)
    } else if (hasStreamSubscription(clazz)) {
      val streamAnn = streamSubscription(clazz).get
      new ConsumerSource.ServiceStreamSource(streamAnn.service(), streamAnn.id(), streamAnn.consumerGroup())
    } else {
      throw new IllegalArgumentException(s"Component [$clazz] is missing a @Consume annotation")
    }
  }

  def consumerDestination(clazz: Class[Consumer]): Option[ConsumerDestination] = {
    if (hasTopicPublication(clazz)) {
      val topicName = findPublicationTopicName(clazz)
      Some(new ConsumerDestination.TopicDestination(topicName))
    } else if (hasStreamPublication(clazz)) {
      val streamAnn = clazz.getAnnotation(classOf[ServiceStream])
      Some(new ConsumerDestination.ServiceStreamDestination(streamAnn.id()))
    } else {
      None
    }
  }

  // TODO: add more validations here
  // we should let users know if components are missing required annotations,
  // eg: Workflow and Entities require @TypeId, View requires @Consume
  def getFactoryFor(component: Class[_]): ComponentDescriptorFactory = {
    if (Reflect.isEntity(component) || Reflect.isWorkflow(component))
      EntityDescriptorFactory
    else if (Reflect.isConsumer(component))
      ConsumerDescriptorFactory
    else if (Reflect.isAgent(component))
      AgentDescriptorFactory
    else
      TimedActionDescriptorFactory
  }
}

private[impl] trait ComponentDescriptorFactory {

  /**
   * Inspect the component class (type), validate the annotations/methods and build a component descriptor for it.
   */
  def buildDescriptorFor(componentClass: Class[_], serializer: JsonSerializer): ComponentDescriptor

}

/**
 * Thrown when the component has incorrect annotations
 */
final case class ValidationException(message: String) extends RuntimeException(message)
