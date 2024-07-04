/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

import kalix.Eventing
import kalix.MethodOptions
import akka.platform.javasdk.annotations.Consume.FromServiceStream
import akka.platform.javasdk.annotations.Query
import akka.platform.javasdk.annotations.Table
import akka.platform.javasdk.impl.ComponentDescriptorFactory.combineBy
import akka.platform.javasdk.impl.ComponentDescriptorFactory.combineByES
import akka.platform.javasdk.impl.ComponentDescriptorFactory.combineByTopic
import akka.platform.javasdk.impl.ComponentDescriptorFactory.eventingInForEventSourcedEntity
import akka.platform.javasdk.impl.ComponentDescriptorFactory.eventingInForEventSourcedEntityServiceLevel
import akka.platform.javasdk.impl.ComponentDescriptorFactory.eventingInForTopic
import akka.platform.javasdk.impl.ComponentDescriptorFactory.eventingInForTopicServiceLevel
import akka.platform.javasdk.impl.ComponentDescriptorFactory.eventingInForValueEntity
import akka.platform.javasdk.impl.ComponentDescriptorFactory.findEventSourcedEntityType
import akka.platform.javasdk.impl.ComponentDescriptorFactory.findHandleDeletes
import akka.platform.javasdk.impl.ComponentDescriptorFactory.findSubscriptionTopicName
import akka.platform.javasdk.impl.ComponentDescriptorFactory.findValueEntityType
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasEventSourcedEntitySubscription
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasHandleDeletes
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasStreamSubscription
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasTopicSubscription
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasUpdateEffectOutput
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasValueEntitySubscription
import akka.platform.javasdk.impl.ComponentDescriptorFactory.mergeServiceOptions
import akka.platform.javasdk.impl.ComponentDescriptorFactory.subscribeToEventStream
import akka.platform.javasdk.impl.JwtDescriptorFactory.buildJWTOptions
import akka.platform.javasdk.impl.reflection.CommandHandlerMethod
import akka.platform.javasdk.impl.reflection.HandleDeletesServiceMethod
import akka.platform.javasdk.impl.reflection.KalixMethod
import akka.platform.javasdk.impl.reflection.NameGenerator
import akka.platform.javasdk.impl.reflection.Reflect
import akka.platform.javasdk.impl.reflection.SubscriptionServiceMethod
import akka.platform.javasdk.impl.reflection.ViewUrlTemplate
import akka.platform.javasdk.impl.reflection.VirtualDeleteServiceMethod
import akka.platform.javasdk.impl.reflection.VirtualServiceMethod
// TODO: abstract away reactor dependency

private[impl] object ViewDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(
      component: Class[_],
      messageCodec: JsonMessageCodec,
      nameGenerator: NameGenerator): ComponentDescriptor = {

    val isMultiTable = Reflect.isMultiTableView(component)

    val tableComponents =
      if (isMultiTable) component.getDeclaredClasses.toSeq.filter(Reflect.isNestedViewTable)
      else Seq(component)

    val (tableTypeDescriptors, updateMethods) = {
      tableComponents
        .map { component =>
          // View class type parameter declares table type
          val tableType: Class[_] =
            component.getGenericSuperclass
              .asInstanceOf[ParameterizedType]
              .getActualTypeArguments
              .head
              .asInstanceOf[Class[_]]

          val tableName: String = component.getAnnotation(classOf[Table]).value()
          val tableTypeDescriptor = ProtoMessageDescriptors.generateMessageDescriptors(tableType)

          val tableProtoMessageName = tableTypeDescriptor.mainMessageDescriptor.getName

          val hasMethodLevelEventSourcedEntitySubs = component.getMethods.exists(hasEventSourcedEntitySubscription)
          val hasTypeLevelEventSourcedEntitySubs = hasEventSourcedEntitySubscription(component)
          val hasTypeLevelValueEntitySubs = hasValueEntitySubscription(component)
          val hasMethodLevelValueEntitySubs = component.getMethods.exists(hasValueEntitySubscription)
          val hasTypeLevelTopicSubs = hasTopicSubscription(component)
          val hasMethodLevelTopicSubs = component.getMethods.exists(hasTopicSubscription)
          val hasTypeLevelStreamSubs = hasStreamSubscription(component)

          val updateMethods = {
            if (hasTypeLevelValueEntitySubs)
              subscriptionForTypeLevelValueEntity(component, tableType, tableName, tableProtoMessageName)
            else if (hasMethodLevelValueEntitySubs)
              subscriptionForMethodLevelValueEntity(component, tableName, tableProtoMessageName)
            else if (hasTypeLevelEventSourcedEntitySubs) {
              val kalixSubscriptionMethods =
                methodsForTypeLevelESSubscriptions(component, tableName, tableProtoMessageName, isMultiTable)
              combineBy("ES", kalixSubscriptionMethods, messageCodec, component)
            } else if (hasMethodLevelEventSourcedEntitySubs) {
              val methodsForMethodLevelESSubscriptions =
                subscriptionEventSourcedEntityMethodLevel(component, tableName, tableProtoMessageName)
              combineByES(methodsForMethodLevelESSubscriptions, messageCodec, component)
            } else if (hasTypeLevelTopicSubs) {
              val kalixSubscriptionMethods =
                methodsForTypeLevelTopicSubscriptions(component, tableName, tableProtoMessageName, isMultiTable)
              combineBy("Topic", kalixSubscriptionMethods, messageCodec, component)
            } else if (hasMethodLevelTopicSubs) {
              val methodsForMethodLevelTopicSubscriptions =
                subscriptionTopicMethodLevel(component, tableName, tableProtoMessageName)
              combineByTopic(methodsForMethodLevelTopicSubscriptions, messageCodec, component)
            } else if (hasTypeLevelStreamSubs) {
              val kalixSubscriptionMethods =
                methodsForTypeLevelStreamSubscriptions(component, tableName, tableProtoMessageName)
              combineBy("Stream", kalixSubscriptionMethods, messageCodec, component)
            } else
              Seq.empty
          }

          tableTypeDescriptor -> updateMethods
        }
        .foldLeft((Seq.empty[ProtoMessageDescriptors], Seq.empty[KalixMethod])) {
          case ((tableTypeDescriptors, allUpdateMethods), (tableTypeDescriptor, updateMethods)) =>
            (tableTypeDescriptors :+ tableTypeDescriptor, allUpdateMethods ++ updateMethods)
        }
    }

    val allQueryMethods = queryMethods(component)

    val kalixMethods: Seq[KalixMethod] = allQueryMethods.map(_.queryMethod) ++ updateMethods
    val serviceName = nameGenerator.getName(component.getSimpleName)
    val additionalMessages =
      tableTypeDescriptors.toSet ++ allQueryMethods.map(_.queryOutputSchemaDescriptor) ++ allQueryMethods.flatMap(
        _.queryInputSchemaDescriptor.toSet)

    val serviceLevelOptions =
      mergeServiceOptions(
        AclDescriptorFactory.serviceLevelAclAnnotation(component, default = Some(AclDescriptorFactory.denyAll)),
        JwtDescriptorFactory.serviceLevelJwtAnnotation(component),
        eventingInForEventSourcedEntityServiceLevel(component),
        eventingInForTopicServiceLevel(component),
        subscribeToEventStream(component))

    ComponentDescriptor(
      nameGenerator,
      messageCodec,
      serviceName,
      serviceOptions = serviceLevelOptions,
      component.getPackageName,
      kalixMethods,
      additionalMessages.toSeq)
  }

  private case class QueryMethod(
      queryMethod: KalixMethod,
      queryInputSchemaDescriptor: Option[ProtoMessageDescriptors],
      queryOutputSchemaDescriptor: ProtoMessageDescriptors)

  private def queryMethods(component: Class[_]): Seq[QueryMethod] = {
    // we only take methods with Query annotations and Spring REST annotations
    val annotatedQueryMethods =
      component.getDeclaredMethods.toIndexedSeq
        .filter(_.getAnnotation(classOf[Query]) != null)

    annotatedQueryMethods.map { queryMethod =>
      val queryOutputType = queryMethod.getReturnType

      val queryOutputSchemaDescriptor =
        ProtoMessageDescriptors.generateMessageDescriptors(queryOutputType)

      // TODO: it should be possible to have fixed queries and use a GET method
      val QueryParametersSchemaDescriptor =
        queryMethod.getParameterTypes.headOption.map { param =>
          ProtoMessageDescriptors.generateMessageDescriptors(param)
        }

      val queryAnnotation = queryMethod.getAnnotation(classOf[Query])
      val queryStr = queryAnnotation.value()

      val query = kalix.View.Query
        .newBuilder()
        .setQuery(queryStr)
        .build()

      val jsonSchema = {
        val builder = kalix.JsonSchema
          .newBuilder()
          .setOutput(queryOutputSchemaDescriptor.mainMessageDescriptor.getName)

        QueryParametersSchemaDescriptor.foreach { inputSchema =>
          builder
            .setInput(inputSchema.mainMessageDescriptor.getName)
            .setJsonBodyInputField("json_body")
        }
        builder.build()
      }

      val view = kalix.View
        .newBuilder()
        .setJsonSchema(jsonSchema)
        .setQuery(query)
        .build()

      val builder = kalix.MethodOptions.newBuilder()
      builder.setView(view)
      val methodOptions = builder.build()

      // since it is a query, we don't actually ever want to handle any request in the SDK
      // the proxy does the work for us, mark the method as non-callable
      // TODO: this new variant can be marked as non-callable - check what is the impact of it
      val servMethod = CommandHandlerMethod(component, queryMethod, ViewUrlTemplate)
      val kalixQueryMethod =
        KalixMethod(servMethod, methodOptions = Some(methodOptions))
          .withKalixOptions(buildJWTOptions(queryMethod))

      QueryMethod(kalixQueryMethod, QueryParametersSchemaDescriptor, queryOutputSchemaDescriptor)
    }
  }

  private def methodsForTypeLevelStreamSubscriptions(
      component: Class[_],
      tableName: String,
      tableProtoMessageName: String): Map[String, Seq[KalixMethod]] = {
    val methods = eligibleSubscriptionMethods(component, tableName, tableProtoMessageName, None).toIndexedSeq
    val ann = component.getAnnotation(classOf[FromServiceStream])
    val key = ann.id().capitalize
    Map(key -> methods)
  }

  private def methodsForTypeLevelESSubscriptions(
      component: Class[_],
      tableName: String,
      tableProtoMessageName: String,
      isMultiTable: Boolean): Map[String, Seq[KalixMethod]] = {

    val methods = eligibleSubscriptionMethods(
      component,
      tableName,
      tableProtoMessageName,
      if (isMultiTable) Some(eventingInForEventSourcedEntity(component)) else None).toIndexedSeq
    val entityType = findEventSourcedEntityType(component)
    Map(entityType -> methods)
  }

  private def methodsForTypeLevelTopicSubscriptions(
      component: Class[_],
      tableName: String,
      tableProtoMessageName: String,
      isMultiTable: Boolean): Map[String, Seq[KalixMethod]] = {

    val methods = eligibleSubscriptionMethods(
      component,
      tableName,
      tableProtoMessageName,
      if (isMultiTable) Some(eventingInForTopic(component)) else None).toIndexedSeq
    val entityType = findSubscriptionTopicName(component)
    Map(entityType -> methods)
  }

  private def eligibleSubscriptionMethods(
      component: Class[_],
      tableName: String,
      tableProtoMessageName: String,
      eventing: Option[Eventing]) =
    component.getMethods.filter(hasUpdateEffectOutput).map { method =>
      // event sourced or topic subscription updates
      val methodOptionsBuilder = kalix.MethodOptions.newBuilder()

      eventing.foreach(methodOptionsBuilder.setEventing)

      addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, true)

      KalixMethod(SubscriptionServiceMethod(method))
        .withKalixOptions(methodOptionsBuilder.build())
    }

  private def subscriptionEventSourcedEntityMethodLevel(
      component: Class[_],
      tableName: String,
      tableProtoMessageName: String): Seq[KalixMethod] = {

    def getMethodsWithSubscription(component: Class[_]): Seq[Method] = {
      import akka.platform.javasdk.impl.reflection.Reflect.methodOrdering
      component.getMethods
        .filter(hasEventSourcedEntitySubscription)
        .sorted
        .toIndexedSeq
    }

    def getEventing(method: Method, component: Class[_]): Eventing =
      if (hasEventSourcedEntitySubscription(component)) eventingInForEventSourcedEntity(component)
      else eventingInForEventSourcedEntity(method)

    getMethodsWithSubscription(component).map { method =>
      // event sourced or topic subscription updates
      val methodOptionsBuilder = kalix.MethodOptions.newBuilder()

      if (hasEventSourcedEntitySubscription(method))
        methodOptionsBuilder.setEventing(getEventing(method, component))

      addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, true)

      KalixMethod(SubscriptionServiceMethod(method))
        .withKalixOptions(methodOptionsBuilder.build())
    }
  }

  private def subscriptionTopicMethodLevel(
      component: Class[_],
      tableName: String,
      tableProtoMessageName: String): Seq[KalixMethod] = {

    def getMethodsWithSubscription(component: Class[_]): Seq[Method] = {
      import Reflect.methodOrdering
      component.getMethods
        .filter(hasTopicSubscription)
        .sorted
        .toIndexedSeq
    }

    getMethodsWithSubscription(component).map { method =>
      // event sourced or topic subscription updates
      val methodOptionsBuilder = kalix.MethodOptions.newBuilder()

      methodOptionsBuilder.setEventing(eventingInForTopic(method))

      addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, true)

      KalixMethod(SubscriptionServiceMethod(method))
        .withKalixOptions(methodOptionsBuilder.build())
    }
  }

  private def subscriptionForMethodLevelValueEntity(
      component: Class[_],
      tableName: String,
      tableProtoMessageName: String): Seq[KalixMethod] = {

    import Reflect.methodOrdering

    val handleDeletesMethods = component.getMethods
      .filter(hasHandleDeletes)
      .sorted
      .map { method =>
        val methodOptionsBuilder = kalix.MethodOptions.newBuilder()
        methodOptionsBuilder.setEventing(eventingInForValueEntity(method))
        addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, transform = true)

        KalixMethod(HandleDeletesServiceMethod(method))
          .withKalixOptions(methodOptionsBuilder.build())
          .withKalixOptions(buildJWTOptions(method))
      }

    val valueEntitySubscriptionMethods = component.getMethods
      .filterNot(hasHandleDeletes)
      .filter(hasValueEntitySubscription)
      .sorted // make sure we get the methods in deterministic order
      .map { method =>

        val methodOptionsBuilder = kalix.MethodOptions.newBuilder()
        methodOptionsBuilder.setEventing(eventingInForValueEntity(method))
        addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, transform = true)

        KalixMethod(SubscriptionServiceMethod(method))
          .withKalixOptions(methodOptionsBuilder.build())
          .withKalixOptions(buildJWTOptions(method))
      }

    (handleDeletesMethods ++ valueEntitySubscriptionMethods).toSeq
  }

  private def subscriptionForTypeLevelValueEntity(
      component: Class[_],
      tableType: Class[_],
      tableName: String,
      tableProtoMessageName: String) = {
    // create a virtual method
    val methodOptionsBuilder = kalix.MethodOptions.newBuilder()

    val entityType = findValueEntityType(component)
    methodOptionsBuilder.setEventing(eventingInForValueEntity(entityType, handleDeletes = false))

    addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, transform = false)
    val kalixOptions = methodOptionsBuilder.build()

    if (findHandleDeletes(component)) {
      val deleteMethodOptionsBuilder = kalix.MethodOptions.newBuilder()
      deleteMethodOptionsBuilder.setEventing(eventingInForValueEntity(entityType, handleDeletes = true))
      addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, deleteMethodOptionsBuilder, transform = false)
      Seq(
        KalixMethod(VirtualServiceMethod(component, "OnChange", tableType)).withKalixOptions(kalixOptions),
        KalixMethod(VirtualDeleteServiceMethod(component, "OnDelete")).withKalixOptions(
          deleteMethodOptionsBuilder.build()))
    } else {
      Seq(KalixMethod(VirtualServiceMethod(component, "OnChange", tableType)).withKalixOptions(kalixOptions))
    }
  }

  private def addTableOptionsToUpdateMethod(
      tableName: String,
      tableProtoMessage: String,
      builder: MethodOptions.Builder,
      transform: Boolean) = {
    val update = kalix.View.Update
      .newBuilder()
      .setTable(tableName)
      .setTransformUpdates(transform)

    val jsonSchema = kalix.JsonSchema
      .newBuilder()
      .setOutput(tableProtoMessage)
      .build()

    val view = kalix.View
      .newBuilder()
      .setUpdate(update)
      .setJsonSchema(jsonSchema)
      .build()
    builder.setView(view)
  }

}
