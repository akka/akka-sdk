/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

import akka.annotation.InternalApi
import akka.javasdk.annotations.Acl
import akka.javasdk.annotations.ComponentId
import akka.javasdk.annotations.Consume.FromEventSourcedEntity
import akka.javasdk.annotations.Consume.FromKeyValueEntity
import akka.javasdk.annotations.Consume.FromServiceStream
import akka.javasdk.annotations.Consume.FromTopic
import akka.javasdk.annotations.DeleteHandler
import akka.javasdk.annotations.Produce.ServiceStream
import akka.javasdk.annotations.Produce.ToTopic
import akka.javasdk.consumer.Consumer
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.reflection.CombinedSubscriptionServiceMethod
import akka.javasdk.impl.reflection.KalixMethod
import akka.javasdk.impl.reflection.NameGenerator
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.view.ViewDescriptorFactory
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.view.TableUpdater
import akka.javasdk.view.View
import akka.javasdk.workflow.Workflow
import akka.runtime.sdk.spi.ConsumerDestination
import akka.runtime.sdk.spi.ConsumerSource
import kalix.DirectSource
import kalix.EventSource
import kalix.Eventing
import kalix.ServiceEventing
import kalix.ServiceOptions
// TODO: abstract away spring dependency
import akka.javasdk.impl.reflection.Reflect.Syntax._

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ComponentDescriptorFactory {

  def hasAcl(javaMethod: Method): Boolean =
    javaMethod.isPublic && javaMethod.hasAnnotation[Acl]

  def hasValueEntitySubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromKeyValueEntity]

  def hasEventSourcedEntitySubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromEventSourcedEntity]

  def streamSubscription(clazz: Class[_]): Option[FromServiceStream] =
    clazz.getAnnotationOption[FromServiceStream]

  def hasSubscription(clazz: Class[_]): Boolean = {
    hasValueEntitySubscription(clazz) ||
    hasEventSourcedEntitySubscription(clazz) ||
    hasTopicSubscription(clazz) ||
    hasStreamSubscription(clazz)
  }

  def eventSourcedEntitySubscription(clazz: Class[_]): Option[FromEventSourcedEntity] =
    clazz.getAnnotationOption[FromEventSourcedEntity]

  def topicSubscription(clazz: Class[_]): Option[FromTopic] =
    clazz.getAnnotationOption[FromTopic]

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
    javaMethod.isPublic && javaMethod.getReturnType == classOf[KeyValueEntity.Effect[_]]
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

  def readComponentIdIdValue(annotated: AnnotatedElement): String = {
    val annotation = annotated.getAnnotation(classOf[ComponentId])
    if (annotation eq null)
      throw new IllegalArgumentException(
        s"Component [$annotated] is missing ${classOf[ComponentId].getName} annotation")
    else annotation.value()
  }

  def findEventSourcedEntityClass(javaMethod: Method): Class[_ <: EventSourcedEntity[_, _]] = {
    val ann = javaMethod.getAnnotation(classOf[FromEventSourcedEntity])
    ann.value()
  }

  def findEventSourcedEntityType(clazz: Class[_]): String = {
    val ann = clazz.getAnnotation(classOf[FromEventSourcedEntity])
    readComponentIdIdValue(ann.value())
  }

  def findValueEntityType(component: Class[_]): String = {
    val ann = component.getAnnotation(classOf[FromKeyValueEntity])
    readComponentIdIdValue(ann.value())
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
    if (hasValueEntitySubscription(clazz)) {
      val kveType = findValueEntityType(clazz)
      new ConsumerSource.KeyValueEntitySource(kveType)
    } else if (hasEventSourcedEntitySubscription(clazz)) {
      val esType = findEventSourcedEntityType(clazz)
      new ConsumerSource.EventSourcedEntitySource(esType)
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

  def eventingInForEventSourcedEntity(clazz: Class[_]): Eventing = {
    val entityType = findEventSourcedEntityType(clazz)
    val eventSource = EventSource.newBuilder().setEventSourcedEntity(entityType).build()
    Eventing.newBuilder().setIn(eventSource).build()
  }

  def eventingInForTopic(clazz: Class[_]): Eventing = {
    Eventing.newBuilder().setIn(topicEventSource(clazz)).build()
  }

  def eventingInForEventSourcedEntityServiceLevel(clazz: Class[_]): Option[kalix.ServiceOptions] = {
    eventSourcedEntitySubscription(clazz).map { _ =>
      val entityType = findEventSourcedEntityType(clazz)
      val in = EventSource.newBuilder().setEventSourcedEntity(entityType)
      val eventing = ServiceEventing.newBuilder().setIn(in)
      kalix.ServiceOptions.newBuilder().setEventing(eventing).build()
    }
  }

  def eventingInForTopicServiceLevel(clazz: Class[_]): Option[kalix.ServiceOptions] = {
    topicSubscription(clazz).map { ann =>
      val in = EventSource.newBuilder().setTopic(ann.value()).setConsumerGroup(ann.consumerGroup())
      val eventing = ServiceEventing.newBuilder().setIn(in)
      kalix.ServiceOptions.newBuilder().setEventing(eventing).build()
    }
  }

  def topicEventSource(clazz: Class[_]): EventSource = {
    val topicName = findSubscriptionTopicName(clazz)
    val consumerGroup = findSubscriptionConsumerGroup(clazz)
    EventSource.newBuilder().setTopic(topicName).setConsumerGroup(consumerGroup).build()
  }

  def eventingInForValueEntity(clazz: Class[_], handleDeletes: Boolean): Eventing = {
    val entityType = findValueEntityType(clazz)
    val eventSource = EventSource
      .newBuilder()
      .setValueEntity(entityType)
      .setHandleDeletes(handleDeletes)
      .build()
    Eventing.newBuilder().setIn(eventSource).build()
  }

  def subscribeToEventStream(component: Class[_]): Option[kalix.ServiceOptions] = {
    Option(component.getAnnotation(classOf[FromServiceStream])).map { streamAnn =>
      val direct = DirectSource
        .newBuilder()
        .setEventStreamId(streamAnn.id())
        .setService(streamAnn.service())

      val in = EventSource
        .newBuilder()
        .setDirect(direct)
        .setConsumerGroup(streamAnn.consumerGroup())

      val eventing =
        ServiceEventing
          .newBuilder()
          .setIn(in)

      kalix.ServiceOptions
        .newBuilder()
        .setEventing(eventing)
        .build()
    }
  }

  // TODO: add more validations here
  // we should let users know if components are missing required annotations,
  // eg: Workflow and Entities require @TypeId, View requires @Consume
  def getFactoryFor(component: Class[_]): ComponentDescriptorFactory = {
    if (Reflect.isEntity(component) || Reflect.isWorkflow(component))
      EntityDescriptorFactory
    else if (Reflect.isView(component))
      ViewDescriptorFactory
    else if (Reflect.isConsumer(component))
      ConsumerDescriptorFactory
    else
      TimedActionDescriptorFactory
  }

  def combineBy(
      sourceName: String,
      groupedSubscriptions: Map[String, Seq[KalixMethod]],
      serializer: JsonSerializer,
      component: Class[_]): Seq[KalixMethod] = {

    groupedSubscriptions.collect {
      case (source, kMethods) if kMethods.size > 1 =>
        val methodsMap =
          kMethods.flatMap { k =>
            val methodParameterTypes = k.serviceMethod.javaMethodOpt.get.getParameterTypes
            // it is safe to pick the last parameter. An action has one and View has two. In the View always the last is the event
            val eventParameter = methodParameterTypes.last

            serializer.contentTypesFor(eventParameter).map(typeUrl => (typeUrl, k.serviceMethod.javaMethodOpt.get))
          }.toMap

        KalixMethod(
          CombinedSubscriptionServiceMethod(
            component.getName,
            "KalixSyntheticMethodOn" + sourceName + escapeMethodName(source.capitalize),
            methodsMap))
          .withKalixOptions(kMethods.head.methodOptions)

      case (source, kMethod +: Nil) =>
        //only here it makes sense to check if the input is sealed, since kMethod size is 1
        if (kMethod.serviceMethod.javaMethodOpt.exists(_.getParameterTypes.last.isSealed)) {
          val javaMethod = kMethod.serviceMethod.javaMethodOpt.get
          val methodsMap = javaMethod.getParameterTypes.last.getPermittedSubclasses.toList.flatMap { subClass =>
            serializer.contentTypesFor(subClass).map(typeUrl => (typeUrl, javaMethod))
          }.toMap
          KalixMethod(
            CombinedSubscriptionServiceMethod(
              component.getName,
              "KalixSyntheticMethodOn" + sourceName + escapeMethodName(source.capitalize),
              methodsMap))
            .withKalixOptions(kMethod.methodOptions)
        } else {
          kMethod
        }
    }.toSeq
  }

  private[impl] def escapeMethodName(value: String): String = {
    value.replaceAll("[\\._\\-]", "")
  }

  def mergeServiceOptions(allOptions: Option[kalix.ServiceOptions]*): Option[ServiceOptions] = {
    val mergedOptions =
      allOptions.flatten
        .foldLeft(kalix.ServiceOptions.newBuilder()) { case (builder, serviceOptions) =>
          builder.mergeFrom(serviceOptions)
        }
        .build()

    // if builder produces the default one, we can returns a None
    if (mergedOptions == kalix.ServiceOptions.getDefaultInstance) None
    else Some(mergedOptions)
  }
}

private[impl] trait ComponentDescriptorFactory {

  /**
   * Inspect the component class (type), validate the annotations/methods and build a component descriptor for it.
   */
  def buildDescriptorFor(
      componentClass: Class[_],
      serializer: JsonSerializer,
      nameGenerator: NameGenerator): ComponentDescriptor

}

/**
 * Thrown when the component has incorrect annotations
 */
final case class ValidationException(message: String) extends RuntimeException(message)
