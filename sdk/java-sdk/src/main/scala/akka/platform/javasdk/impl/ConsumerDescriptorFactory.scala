/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import akka.platform.javasdk.impl
import akka.platform.javasdk.impl.ComponentDescriptorFactory.buildEventingOutOptions
import akka.platform.javasdk.impl.ComponentDescriptorFactory.combineBy
import akka.platform.javasdk.impl.ComponentDescriptorFactory.eventingInForEventSourcedEntityServiceLevel
import akka.platform.javasdk.impl.ComponentDescriptorFactory.findEventSourcedEntityType
import akka.platform.javasdk.impl.ComponentDescriptorFactory.findSubscriptionTopicName
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasConsumerOutput
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasEventSourcedEntitySubscription
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasHandleDeletes
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasTopicSubscription
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasValueEntitySubscription
import akka.platform.javasdk.impl.ComponentDescriptorFactory.mergeServiceOptions
import akka.platform.javasdk.impl.ComponentDescriptorFactory.publishToEventStream
import akka.platform.javasdk.impl.ComponentDescriptorFactory.streamSubscription
import akka.platform.javasdk.impl.ComponentDescriptorFactory.subscribeToEventStream
import akka.platform.javasdk.impl.ComponentDescriptorFactory.topicEventDestination
import akka.platform.javasdk.impl.ComponentDescriptorFactory.topicEventSource
import akka.platform.javasdk.impl.ComponentDescriptorFactory.valueEntityEventSource
import akka.platform.javasdk.impl.reflection.HandleDeletesServiceMethod
import akka.platform.javasdk.impl.reflection.KalixMethod
import akka.platform.javasdk.impl.reflection.NameGenerator
import akka.platform.javasdk.impl.reflection.Reflect
import akka.platform.javasdk.impl.reflection.SubscriptionServiceMethod
import kalix.EventSource
import kalix.Eventing
import kalix.MethodOptions

private[impl] object ConsumerDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(
      component: Class[_],
      messageCodec: JsonMessageCodec,
      nameGenerator: NameGenerator): ComponentDescriptor = {

    def withOptionalDestination(clazz: Class[_], source: EventSource): MethodOptions = {
      val eventingBuilder = Eventing.newBuilder().setIn(source)
      topicEventDestination(clazz).foreach(eventingBuilder.setOut)
      kalix.MethodOptions.newBuilder().setEventing(eventingBuilder.build()).build()
    }

    import Reflect.methodOrdering

    val handleDeletesMethods = component.getMethods
      .filter(hasConsumerOutput)
      .filter(hasHandleDeletes)
      .sorted
      .map { method =>
        val source = valueEntityEventSource(component, handleDeletes = true)
        val kalixOptions = withOptionalDestination(component, source)
        KalixMethod(HandleDeletesServiceMethod(method))
          .withKalixOptions(kalixOptions)
      }
      .toSeq

    val subscriptionValueEntityMethods: IndexedSeq[KalixMethod] = if (hasValueEntitySubscription(component)) {
      //expecting only a single update method, which is validated
      component.getMethods
        .filter(hasConsumerOutput)
        .filterNot(hasHandleDeletes)
        .map { method =>
          val source = valueEntityEventSource(component, handleDeletes = false)
          val kalixOptions = withOptionalDestination(component, source)
          KalixMethod(SubscriptionServiceMethod(method))
            .withKalixOptions(kalixOptions)
        }
        .toIndexedSeq
    } else {
      IndexedSeq.empty[KalixMethod]
    }

    val subscriptionEventSourcedEntityClass: Map[String, Seq[KalixMethod]] =
      if (hasEventSourcedEntitySubscription(component)) {
        val kalixMethods =
          component.getMethods
            .filter(hasConsumerOutput)
            .sorted // make sure we get the methods in deterministic order
            .map { method =>
              KalixMethod(SubscriptionServiceMethod(method))
                .withKalixOptions(buildEventingOutOptions(component))
            }
            .toSeq

        val entityType = findEventSourcedEntityType(component)
        Map(entityType -> kalixMethods)

      } else Map.empty

    val subscriptionStreamClass: Map[String, Seq[KalixMethod]] = {
      streamSubscription(component)
        .map { ann =>
          val kalixMethods =
            component.getMethods
              .filter(hasConsumerOutput)
              .sorted // make sure we get the methods in deterministic order
              .map { method =>
                KalixMethod(SubscriptionServiceMethod(method))
                  .withKalixOptions(buildEventingOutOptions(component))
              }
              .toSeq

          val streamId = ann.id()
          Map(streamId -> kalixMethods)
        }
        .getOrElse(Map.empty)
    }

    // type level @Consume.FormTopic, methods eligible for subscription
    val subscriptionTopicClass: Map[String, Seq[KalixMethod]] =
      if (hasTopicSubscription(component)) {
        val kalixMethods = component.getMethods
          .filter(hasConsumerOutput)
          .sorted // make sure we get the methods in deterministic order
          .map { method =>
            val source = topicEventSource(component)
            val kalixOptions = withOptionalDestination(component, source)
            KalixMethod(SubscriptionServiceMethod(method))
              .withKalixOptions(kalixOptions)
          }
          .toIndexedSeq
        val topicName = findSubscriptionTopicName(component)
        Map(topicName -> kalixMethods)
      } else Map.empty

    val serviceName = nameGenerator.getName(component.getSimpleName)

    val serviceLevelOptions =
      mergeServiceOptions(
        AclDescriptorFactory.serviceLevelAclAnnotation(component),
        eventingInForEventSourcedEntityServiceLevel(component),
        subscribeToEventStream(component),
        publishToEventStream(component))

    impl.ComponentDescriptor(
      nameGenerator,
      messageCodec,
      serviceName,
      serviceOptions = serviceLevelOptions,
      component.getPackageName,
      handleDeletesMethods
      ++ subscriptionValueEntityMethods
      ++ combineBy("ES", subscriptionEventSourcedEntityClass, messageCodec, component)
      ++ combineBy("Stream", subscriptionStreamClass, messageCodec, component)
      ++ combineBy("Topic", subscriptionTopicClass, messageCodec, component))
  }
}