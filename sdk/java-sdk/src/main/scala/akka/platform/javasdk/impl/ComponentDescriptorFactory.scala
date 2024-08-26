/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import akka.platform.javasdk.timedaction.TimedAction
import akka.platform.javasdk.annotations.Acl
import akka.platform.javasdk.annotations.ComponentId
import akka.platform.javasdk.annotations.Consume.FromEventSourcedEntity
import akka.platform.javasdk.annotations.Consume.FromKeyValueEntity
import akka.platform.javasdk.annotations.Consume.FromServiceStream
import akka.platform.javasdk.annotations.Consume.FromTopic
import akka.platform.javasdk.annotations.DeleteHandler
import akka.platform.javasdk.annotations.Produce.ServiceStream
import akka.platform.javasdk.annotations.Produce.ToTopic
import akka.platform.javasdk.consumer.Consumer
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity
import akka.platform.javasdk.impl.reflection.CombinedSubscriptionServiceMethod
import akka.platform.javasdk.impl.reflection.KalixMethod
import akka.platform.javasdk.impl.reflection.NameGenerator
import akka.platform.javasdk.impl.reflection.Reflect
import akka.platform.javasdk.view.TableUpdater
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

  def hasActionOutput(javaMethod: Method): Boolean = {
    if (javaMethod.isPublic) {
      javaMethod.getReturnType.isAssignableFrom(classOf[TimedAction.Effect])
    } else {
      false
    }
  }

  def hasConsumerOutput(javaMethod: Method): Boolean = {
    if (javaMethod.isPublic) {
      javaMethod.getReturnType.isAssignableFrom(classOf[Consumer.Effect])
    } else {
      false
    }
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

  def readComponentIdIdValue(annotated: AnnotatedElement): String =
    annotated.getAnnotation(classOf[ComponentId]).value()

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

  def valueEntityEventSource(clazz: Class[_], handleDeletes: Boolean) = {
    val entityType = findValueEntityType(clazz)
    EventSource
      .newBuilder()
      .setValueEntity(entityType)
      .setHandleDeletes(handleDeletes)
      .build()
  }

  def topicEventDestination(clazz: Class[_]): Option[EventDestination] = {
    if (hasTopicPublication(clazz)) {
      val topicName = findPublicationTopicName(clazz)
      Some(EventDestination.newBuilder().setTopic(topicName).build())
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

  def eventingOutForTopic(clazz: Class[_]): Option[Eventing] = {
    topicEventDestination(clazz).map(eventSource => Eventing.newBuilder().setOut(eventSource).build())
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
    else if (Reflect.isConsumer(component))
      ConsumerDescriptorFactory
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

  private[impl] def buildEventingOutOptions(clazz: Class[_]): Option[MethodOptions] =
    eventingOutForTopic(clazz)
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
