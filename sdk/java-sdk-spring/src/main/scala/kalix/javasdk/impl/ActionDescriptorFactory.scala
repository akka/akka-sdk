/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl

import kalix.EventSource
import kalix.Eventing
import kalix.MethodOptions
import kalix.javasdk.action.Action
import kalix.javasdk.impl
import kalix.javasdk.impl.ComponentDescriptorFactory.buildEventingOutOptions
import kalix.javasdk.impl.ComponentDescriptorFactory.combineBy
import kalix.javasdk.impl.ComponentDescriptorFactory.combineByES
import kalix.javasdk.impl.ComponentDescriptorFactory.combineByTopic
import kalix.javasdk.impl.ComponentDescriptorFactory.eventSourceEntityEventSource
import kalix.javasdk.impl.ComponentDescriptorFactory.eventingInForEventSourcedEntityServiceLevel
import kalix.javasdk.impl.ComponentDescriptorFactory.eventingInForValueEntityServiceLevel
import kalix.javasdk.impl.ComponentDescriptorFactory.eventingOutForTopic
import kalix.javasdk.impl.ComponentDescriptorFactory.findEventSourcedEntityType
import kalix.javasdk.impl.ComponentDescriptorFactory.findSubscriptionTopicName
import kalix.javasdk.impl.ComponentDescriptorFactory.hasActionOutput
import kalix.javasdk.impl.ComponentDescriptorFactory.hasEventSourcedEntitySubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.hasHandleDeletes
import kalix.javasdk.impl.ComponentDescriptorFactory.hasTopicSubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.hasValueEntitySubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.mergeServiceOptions
import kalix.javasdk.impl.ComponentDescriptorFactory.publishToEventStream
import kalix.javasdk.impl.ComponentDescriptorFactory.streamSubscription
import kalix.javasdk.impl.ComponentDescriptorFactory.subscribeToEventStream
import kalix.javasdk.impl.ComponentDescriptorFactory.topicEventDestination
import kalix.javasdk.impl.ComponentDescriptorFactory.topicEventSource
import kalix.javasdk.impl.ComponentDescriptorFactory.valueEntityEventSource
import kalix.javasdk.impl.reflection.ActionHandlerMethod
import kalix.javasdk.impl.reflection.HandleDeletesServiceMethod
import kalix.javasdk.impl.reflection.KalixMethod
import kalix.javasdk.impl.reflection.NameGenerator
import kalix.javasdk.impl.reflection.Reflect
import kalix.javasdk.impl.reflection.Reflect.Syntax.MethodOps
import kalix.javasdk.impl.reflection.SubscriptionServiceMethod

import java.lang.reflect.Method
import scala.reflect.ClassTag

private[impl] object ActionDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(
      component: Class[_],
      messageCodec: JsonMessageCodec,
      nameGenerator: NameGenerator): ComponentDescriptor = {

    // command handlers candidate must be public and have 0 or 1 parameter and return the components effect type
    def isCommandHandlerCandidate[E](method: Method)(implicit effectType: ClassTag[E]): Boolean =
      method.isPublic &&
      effectType.runtimeClass.isAssignableFrom(method.getReturnType) &&
      method.getParameterTypes.length <= 1

    def withOptionalDestination(method: Method, source: EventSource): MethodOptions = {
      val eventingBuilder = Eventing.newBuilder().setIn(source)
      topicEventDestination(method).foreach(eventingBuilder.setOut)
      kalix.MethodOptions.newBuilder().setEventing(eventingBuilder.build()).build()
    }

    import Reflect.methodOrdering

    val handleDeletesMethods = component.getMethods
      .filter(hasHandleDeletes)
      .sorted
      .map { method =>
        val source = valueEntityEventSource(method)
        val kalixOptions = withOptionalDestination(method, source)
        KalixMethod(HandleDeletesServiceMethod(method))
          .withKalixOptions(kalixOptions)
      }

    val subscriptionValueEntityMethods: IndexedSeq[KalixMethod] = if (hasValueEntitySubscription(component)) {
      //expecting only a single update method, which is validated
      component.getMethods
        .filter(hasActionOutput)
        .map { method =>
          KalixMethod(SubscriptionServiceMethod(method))
            .withKalixOptions(buildEventingOutOptions(method))
        }
        .toIndexedSeq
    } else {
      component.getMethods
        .filterNot(hasHandleDeletes)
        .filter(hasValueEntitySubscription)
        .sorted // make sure we get the methods in deterministic order
        .map { method =>
          val source = valueEntityEventSource(method)
          val kalixOptions = withOptionalDestination(method, source)
          KalixMethod(SubscriptionServiceMethod(method))
            .withKalixOptions(kalixOptions)
        }
        .toIndexedSeq
    }

    // methods annotated with @Consume.FromEventSourcedEntity
    val subscriptionEventSourcedEntityMethods: IndexedSeq[KalixMethod] = component.getMethods
      .filter(hasEventSourcedEntitySubscription)
      .sorted // make sure we get the methods in deterministic order
      .map { method =>
        val source = eventSourceEntityEventSource(method)
        val kalixOptions = withOptionalDestination(method, source)
        KalixMethod(SubscriptionServiceMethod(method))
          .withKalixOptions(kalixOptions)
      }
      .toIndexedSeq

    val subscriptionEventSourcedEntityClass: Map[String, Seq[KalixMethod]] =
      if (hasEventSourcedEntitySubscription(component)) {
        val kalixMethods =
          component.getMethods
            .filter(hasActionOutput)
            .sorted // make sure we get the methods in deterministic order
            .map { method =>
              KalixMethod(SubscriptionServiceMethod(method))
                .withKalixOptions(buildEventingOutOptions(method))
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
              .filter(hasActionOutput)
              .sorted // make sure we get the methods in deterministic order
              .map { method =>
                KalixMethod(SubscriptionServiceMethod(method))
                  .withKalixOptions(buildEventingOutOptions(method))
              }
              .toSeq

          val streamId = ann.id()
          Map(streamId -> kalixMethods)
        }
        .getOrElse(Map.empty)
    }

    // methods annotated with @Consume.FormTopic
    val subscriptionTopicMethods: IndexedSeq[KalixMethod] = component.getMethods
      .filter(hasTopicSubscription)
      .sorted // make sure we get the methods in deterministic order
      .map { method =>
        val source = topicEventSource(method)
        val kalixOptions = withOptionalDestination(method, source)
        KalixMethod(SubscriptionServiceMethod(method))
          .withKalixOptions(kalixOptions)
      }
      .toIndexedSeq

    // type level @Consume.FormTopic, methods eligible for subscription
    val subscriptionTopicClass: Map[String, Seq[KalixMethod]] =
      if (hasTopicSubscription(component)) {
        val kalixMethods = component.getMethods
          .filter(hasActionOutput)
          .sorted // make sure we get the methods in deterministic order
          .map { method =>
            val source = topicEventSource(component)
            val kalixOptions = withOptionalDestination(method, source)
            KalixMethod(SubscriptionServiceMethod(method))
              .withKalixOptions(kalixOptions)
          }
          .toIndexedSeq
        val topicName = findSubscriptionTopicName(component)
        Map(topicName -> kalixMethods)
      } else Map.empty

    val alreadyCoveredMethods: Set[Method] =
      (handleDeletesMethods ++ subscriptionValueEntityMethods ++ subscriptionEventSourcedEntityMethods ++ subscriptionEventSourcedEntityClass.values.flatten ++ subscriptionStreamClass.values.flatten ++ subscriptionTopicMethods ++ subscriptionTopicClass.values.flatten)
        .flatMap(_.serviceMethod.javaMethodOpt)
        .toSet

    // all public methods with still unclaimed effect returns
    val commandHandlerMethods = component.getDeclaredMethods
      .filterNot(alreadyCoveredMethods)
      .collect {
        case method if isCommandHandlerCandidate[Action.Effect[_]](method) =>
          val servMethod = ActionHandlerMethod(component, method)
          val optionsBuilder = kalix.MethodOptions.newBuilder()
          eventingOutForTopic(method).foreach(optionsBuilder.setEventing)
          JwtDescriptorFactory.jwtOptions(method).foreach(optionsBuilder.setJwt)
          KalixMethod(servMethod, entityIds = Seq.empty)
            .withKalixOptions(optionsBuilder.build())
      }
      .toIndexedSeq

    val serviceName = nameGenerator.getName(component.getSimpleName)

    val serviceLevelOptions =
      mergeServiceOptions(
        AclDescriptorFactory.serviceLevelAclAnnotation(component),
        JwtDescriptorFactory.serviceLevelJwtAnnotation(component),
        eventingInForEventSourcedEntityServiceLevel(component),
        eventingInForValueEntityServiceLevel(component),
        subscribeToEventStream(component),
        publishToEventStream(component))

    impl.ComponentDescriptor(
      nameGenerator,
      messageCodec,
      serviceName,
      serviceOptions = serviceLevelOptions,
      component.getPackageName,
      commandHandlerMethods
      ++ handleDeletesMethods
      ++ subscriptionValueEntityMethods
      ++ combineByES(subscriptionEventSourcedEntityMethods, messageCodec, component)
      ++ combineByTopic(subscriptionTopicMethods, messageCodec, component)
      ++ combineBy("ES", subscriptionEventSourcedEntityClass, messageCodec, component)
      ++ combineBy("Stream", subscriptionStreamClass, messageCodec, component)
      ++ combineBy("Topic", subscriptionTopicClass, messageCodec, component))
  }
}
