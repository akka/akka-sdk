/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

import akka.platform.javasdk.action.Action
import akka.platform.javasdk.annotations.Acl
import akka.platform.javasdk.annotations.ComponentId
import akka.platform.javasdk.annotations.Consume.FromEventSourcedEntity
import akka.platform.javasdk.annotations.Consume.FromKeyValueEntity
import akka.platform.javasdk.annotations.Consume.FromServiceStream
import akka.platform.javasdk.annotations.Consume.FromTopic
import akka.platform.javasdk.annotations.Produce.ServiceStream
import akka.platform.javasdk.annotations.Produce.ToTopic
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity
import akka.platform.javasdk.impl.reflection.CombinedSubscriptionServiceMethod
import akka.platform.javasdk.impl.reflection.KalixMethod
import akka.platform.javasdk.impl.reflection.NameGenerator
import akka.platform.javasdk.impl.reflection.Reflect
import akka.platform.javasdk.keyvalueentity.KeyValueEntity
import akka.platform.javasdk.view.View.Effect
import kalix.DirectDestination
import kalix.DirectSource
import kalix.EventDestination
import kalix.EventSource
import kalix.Eventing
import kalix.MethodOptions
import kalix.ServiceEventing
import kalix.ServiceEventingOut
import kalix.ServiceOptions
// TODO: abstract away spring dependency
import akka.platform.javasdk.impl.reflection.Reflect.Syntax._

private[impl] object ComponentDescriptorFactory {

  def hasAcl(javaMethod: Method): Boolean =
    javaMethod.isPublic && javaMethod.hasAnnotation[Acl]

  def hasValueEntitySubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromKeyValueEntity]

  def hasValueEntitySubscription(javaMethod: Method): Boolean =
    javaMethod.isPublic && javaMethod.hasAnnotation[FromKeyValueEntity]

  def hasEventSourcedEntitySubscription(javaMethod: Method): Boolean =
    javaMethod.isPublic && javaMethod.hasAnnotation[FromEventSourcedEntity]

  def hasEventSourcedEntitySubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromEventSourcedEntity]

  def streamSubscription(clazz: Class[_]): Option[FromServiceStream] =
    clazz.getAnnotationOption[FromServiceStream]

  def hasSubscription(javaMethod: Method): Boolean = {
    hasValueEntitySubscription(javaMethod) ||
    hasEventSourcedEntitySubscription(javaMethod) ||
    hasTopicSubscription(javaMethod)
  }

  def hasSubscription(clazz: Class[_]): Boolean = {
    hasValueEntitySubscription(clazz) ||
    hasEventSourcedEntitySubscription(clazz) ||
    hasTopicSubscription(clazz) ||
    hasStreamSubscription(clazz)
  }

  private def valueEntitySubscription(clazz: Class[_]): Option[FromKeyValueEntity] =
    clazz.getAnnotationOption[FromKeyValueEntity]

  def eventSourcedEntitySubscription(clazz: Class[_]): Option[FromEventSourcedEntity] =
    clazz.getAnnotationOption[FromEventSourcedEntity]

  def topicSubscription(clazz: Class[_]): Option[FromTopic] =
    clazz.getAnnotationOption[FromTopic]

  def hasActionOutput(javaMethod: Method): Boolean = {
    if (javaMethod.isPublic) {
      javaMethod.getGenericReturnType match {
        case p: ParameterizedType => p.getRawType.equals(classOf[Action.Effect[_]])
        case _                    => false
      }
    } else {
      false
    }
  }

  def hasUpdateEffectOutput(javaMethod: Method): Boolean = {
    if (javaMethod.isPublic) {
      javaMethod.getGenericReturnType match {
        case p: ParameterizedType => p.getRawType.equals(classOf[Effect[_]])
        case _                    => false
      }
    } else {
      false
    }
  }

  def hasTopicSubscription(javaMethod: Method): Boolean =
    javaMethod.isPublic && javaMethod.hasAnnotation[FromTopic]

  def hasHandleDeletes(javaMethod: Method): Boolean = {
    val ann = javaMethod.getAnnotation(classOf[FromKeyValueEntity])
    javaMethod.isPublic && ann != null && ann.handleDeletes()
  }

  def hasTopicSubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromTopic]

  def hasStreamSubscription(clazz: Class[_]): Boolean =
    clazz.isPublic && clazz.hasAnnotation[FromServiceStream]

  def hasTopicPublication(javaMethod: Method): Boolean =
    javaMethod.isPublic && javaMethod.hasAnnotation[ToTopic]

  def readComponentIdIdValue(annotated: AnnotatedElement): String =
    annotated.getAnnotation(classOf[ComponentId]).value()

  def findEventSourcedEntityType(javaMethod: Method): String = {
    val ann = javaMethod.getAnnotation(classOf[FromEventSourcedEntity])
    readComponentIdIdValue(ann.value())
  }

  def findEventSourcedEntityClass(javaMethod: Method): Class[_ <: EventSourcedEntity[_, _]] = {
    val ann = javaMethod.getAnnotation(classOf[FromEventSourcedEntity])
    ann.value()
  }

  private def findValueEntityClass(javaMethod: Method): Class[_ <: KeyValueEntity[_]] = {
    val ann = javaMethod.getAnnotation(classOf[FromKeyValueEntity])
    ann.value()
  }

  def findSubscriptionSourceName(javaMethod: Method): String = {
    if (hasValueEntitySubscription(javaMethod)) {
      findValueEntityClass(javaMethod).getName
    } else if (hasEventSourcedEntitySubscription(javaMethod)) {
      findEventSourcedEntityClass(javaMethod).getName
    } else if (hasTopicSubscription(javaMethod)) {
      "Topic-" + findSubscriptionTopicName(javaMethod)
    } else {
      throw new IllegalStateException("Unsupported source for " + javaMethod.getName)
    }
  }

  def findEventSourcedEntityType(clazz: Class[_]): String = {
    val ann = clazz.getAnnotation(classOf[FromEventSourcedEntity])
    readComponentIdIdValue(ann.value())
  }

  def findValueEntityType(javaMethod: Method): String = {
    val ann = javaMethod.getAnnotation(classOf[FromKeyValueEntity])
    readComponentIdIdValue(ann.value())
  }

  def findValueEntityType(component: Class[_]): String = {
    val ann = component.getAnnotation(classOf[FromKeyValueEntity])
    readComponentIdIdValue(ann.value())
  }

  def findHandleDeletes(javaMethod: Method): Boolean = {
    val ann = javaMethod.getAnnotation(classOf[FromKeyValueEntity])
    ann.handleDeletes()
  }

  def findHandleDeletes(component: Class[_]): Boolean = {
    val ann = component.getAnnotation(classOf[FromKeyValueEntity])
    ann.handleDeletes()
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

  def findPublicationTopicName(javaMethod: Method): String = {
    val ann = javaMethod.getAnnotation(classOf[ToTopic])
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

  def eventingInForValueEntity(javaMethod: Method): Eventing = {
    val eventSource: EventSource = valueEntityEventSource(javaMethod)
    Eventing.newBuilder().setIn(eventSource).build()
  }

  def valueEntityEventSource(javaMethod: Method) = {
    val entityType = findValueEntityType(javaMethod)
    EventSource
      .newBuilder()
      .setValueEntity(entityType)
      .setHandleDeletes(findHandleDeletes(javaMethod))
      .build()
  }

  def topicEventDestination(javaMethod: Method): Option[EventDestination] = {
    if (hasTopicPublication(javaMethod)) {
      val topicName = findPublicationTopicName(javaMethod)
      Some(EventDestination.newBuilder().setTopic(topicName).build())
    } else {
      None
    }
  }

  def eventingInForEventSourcedEntity(javaMethod: Method): Eventing = {
    val eventSource: EventSource = eventSourceEntityEventSource(javaMethod)
    // ignore in method must be always false
    Eventing.newBuilder().setIn(eventSource).build()
  }

  def eventSourceEntityEventSource(javaMethod: Method) = {
    val entityType = findEventSourcedEntityType(javaMethod)
    EventSource.newBuilder().setEventSourcedEntity(entityType).build()
  }

  def eventingInForEventSourcedEntity(clazz: Class[_]): Eventing = {
    val entityType = findEventSourcedEntityType(clazz)
    val eventSource = EventSource.newBuilder().setEventSourcedEntity(entityType).build()
    Eventing.newBuilder().setIn(eventSource).build()
  }

  def eventingInForTopic(clazz: Class[_]): Eventing = {
    Eventing.newBuilder().setIn(topicEventSource(clazz)).build()
  }

  def eventingInForTopic(javaMethod: Method): Eventing = {
    Eventing.newBuilder().setIn(topicEventSource(javaMethod)).build()
  }

  def eventingInForValueEntityServiceLevel(clazz: Class[_]): Option[kalix.ServiceOptions] = {
    valueEntitySubscription(clazz).map { _ =>
      val entityType = findValueEntityType(clazz)
      val in = EventSource.newBuilder().setValueEntity(entityType)
      val eventing = ServiceEventing.newBuilder().setIn(in)
      kalix.ServiceOptions.newBuilder().setEventing(eventing).build()
    }
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

  def topicEventSource(javaMethod: Method): EventSource = {
    val topicName = findSubscriptionTopicName(javaMethod)
    val consumerGroup = findSubscriptionConsumerGroup(javaMethod)
    EventSource.newBuilder().setTopic(topicName).setConsumerGroup(consumerGroup).build()
  }

  def topicEventSource(clazz: Class[_]): EventSource = {
    val topicName = findSubscriptionTopicName(clazz)
    val consumerGroup = findSubscriptionConsumerGroup(clazz)
    EventSource.newBuilder().setTopic(topicName).setConsumerGroup(consumerGroup).build()
  }

  def eventingOutForTopic(javaMethod: Method): Option[Eventing] = {
    topicEventDestination(javaMethod).map(eventSource => Eventing.newBuilder().setOut(eventSource).build())
  }

  def eventingInForValueEntity(entityType: String, handleDeletes: Boolean): Eventing = {
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

  def publishToEventStream(component: Class[_]): Option[kalix.ServiceOptions] = {
    Option(component.getAnnotation(classOf[ServiceStream])).map { streamAnn =>

      val direct = DirectDestination
        .newBuilder()
        .setEventStreamId(streamAnn.id())

      val out = ServiceEventingOut
        .newBuilder()
        .setDirect(direct)

      val eventing =
        ServiceEventing
          .newBuilder()
          .setOut(out)

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
    else
      ActionDescriptorFactory
  }

  def combineByES(
      subscriptions: Seq[KalixMethod],
      messageCodec: JsonMessageCodec,
      component: Class[_]): Seq[KalixMethod] = {

    def groupByES(methods: Seq[KalixMethod]): Map[String, Seq[KalixMethod]] = {
      val withEventSourcedIn = methods.filter(kalixMethod =>
        kalixMethod.methodOptions.exists(option =>
          option.hasEventing && option.getEventing.hasIn && option.getEventing.getIn.hasEventSourcedEntity))
      //Assuming there is only one eventing.in annotation per method, therefore head is as good as any other
      withEventSourcedIn.groupBy(m => m.methodOptions.head.getEventing.getIn.getEventSourcedEntity)
    }

    combineBy("ES", groupByES(subscriptions), messageCodec, component)
  }

  def combineByTopic(
      kalixMethods: Seq[KalixMethod],
      messageCodec: JsonMessageCodec,
      component: Class[_]): Seq[KalixMethod] = {
    def groupByTopic(methods: Seq[KalixMethod]): Map[String, Seq[KalixMethod]] = {
      val withTopicIn = methods.filter(kalixMethod =>
        kalixMethod.methodOptions.exists(option =>
          option.hasEventing && option.getEventing.hasIn && option.getEventing.getIn.hasTopic))
      //Assuming there is only one topic annotation per method, therefore head is as good as any other
      withTopicIn.groupBy(m => m.methodOptions.head.getEventing.getIn.getTopic)
    }

    combineBy("Topic", groupByTopic(kalixMethods), messageCodec, component)
  }

  def combineBy(
      sourceName: String,
      groupedSubscriptions: Map[String, Seq[KalixMethod]],
      messageCodec: JsonMessageCodec,
      component: Class[_]): Seq[KalixMethod] = {

    groupedSubscriptions.collect {
      case (source, kMethods) if kMethods.size > 1 =>
        val methodsMap =
          kMethods.flatMap { k =>
            val methodParameterTypes = k.serviceMethod.javaMethodOpt.get.getParameterTypes
            // it is safe to pick the last parameter. An action has one and View has two. In the View always the last is the event
            val eventParameter = methodParameterTypes.last

            messageCodec.typeUrlsFor(eventParameter).map(typeUrl => (typeUrl, k.serviceMethod.javaMethodOpt.get))
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
            messageCodec.typeUrlsFor(subClass).map(typeUrl => (typeUrl, javaMethod))
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

  private[impl] def buildEventingOutOptions(method: Method): Option[MethodOptions] =
    eventingOutForTopic(method)
      .map(eventingOut => kalix.MethodOptions.newBuilder().setEventing(eventingOut).build())

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
      messageCodec: JsonMessageCodec,
      nameGenerator: NameGenerator): ComponentDescriptor

}

/**
 * Thrown when the component has incorrect annotations
 */
final case class InvalidComponentException(message: String) extends RuntimeException(message)
