/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.reflection.CommandHandlerMethod
import akka.javasdk.impl.reflection.HandleDeletesServiceMethod
import akka.javasdk.impl.reflection.KalixMethod
import akka.javasdk.impl.reflection.NameGenerator
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.reflection.SubscriptionServiceMethod
import akka.javasdk.impl.reflection.ViewUrlTemplate
import akka.javasdk.impl.reflection.VirtualDeleteServiceMethod
import akka.javasdk.impl.reflection.VirtualServiceMethod
import java.lang.reflect.ParameterizedType

import akka.javasdk.annotations.Consume.FromKeyValueEntity
import akka.javasdk.annotations.Consume.FromServiceStream
import ComponentDescriptorFactory.combineBy
import ComponentDescriptorFactory.eventingInForEventSourcedEntity
import ComponentDescriptorFactory.eventingInForEventSourcedEntityServiceLevel
import ComponentDescriptorFactory.eventingInForTopic
import ComponentDescriptorFactory.eventingInForTopicServiceLevel
import ComponentDescriptorFactory.eventingInForValueEntity
import ComponentDescriptorFactory.findEventSourcedEntityType
import ComponentDescriptorFactory.findHandleDeletes
import ComponentDescriptorFactory.findSubscriptionTopicName
import ComponentDescriptorFactory.hasEventSourcedEntitySubscription
import ComponentDescriptorFactory.hasHandleDeletes
import ComponentDescriptorFactory.hasStreamSubscription
import ComponentDescriptorFactory.hasTopicSubscription
import ComponentDescriptorFactory.hasUpdateEffectOutput
import ComponentDescriptorFactory.hasValueEntitySubscription
import ComponentDescriptorFactory.mergeServiceOptions
import ComponentDescriptorFactory.subscribeToEventStream
import JwtDescriptorFactory.buildJWTOptions
import akka.javasdk.annotations.Query
import akka.javasdk.annotations.Table
import akka.javasdk.view.View
import kalix.Eventing
import kalix.MethodOptions
// TODO: abstract away reactor dependency

private[impl] object ViewDescriptorFactory extends ComponentDescriptorFactory {

  val TableNamePattern = """FROM\s+`?([A-Za-z][A-Za-z0-9_]*)""".r

  override def buildDescriptorFor(
      component: Class[_],
      messageCodec: JsonMessageCodec,
      nameGenerator: NameGenerator): ComponentDescriptor = {

    val tableUpdaters =
      component.getDeclaredClasses.toSeq.filter(Reflect.isViewTableUpdater)

    val allQueryMethods = queryMethods(component)

    val (tableTypeDescriptors, updateMethods) = {
      tableUpdaters
        .map { tableUpdater =>
          // View class type parameter declares table type
          val tableType: Class[_] =
            tableUpdater.getGenericSuperclass
              .asInstanceOf[ParameterizedType]
              .getActualTypeArguments
              .head
              .asInstanceOf[Class[_]]

          val queries = allQueryMethods.map(_.queryString)
          val tableName: String = {
            if (tableUpdaters.size > 1) {
              // explicitly annotated since multiple view table updaters
              tableUpdater.getAnnotation(classOf[Table]).value()
            } else {
              // figure out from first query
              val query = queries.head
              TableNamePattern
                .findFirstMatchIn(query)
                .map(_.group(1))
                .getOrElse(throw new RuntimeException(s"Could not extract table name from query [${query}]"))
            }
          }

          val tableTypeDescriptor = ProtoMessageDescriptors.generateMessageDescriptors(tableType)

          val tableProtoMessageName = tableTypeDescriptor.mainMessageDescriptor.getName

          val updateMethods = {
            def hasTypeLevelEventSourcedEntitySubs = hasEventSourcedEntitySubscription(tableUpdater)

            def hasTypeLevelValueEntitySubs = hasValueEntitySubscription(tableUpdater)

            def hasTypeLevelTopicSubs = hasTopicSubscription(tableUpdater)

            def hasTypeLevelStreamSubs = hasStreamSubscription(tableUpdater)

            if (hasTypeLevelValueEntitySubs)
              subscriptionForTypeLevelValueEntity(tableUpdater, tableType, tableName, tableProtoMessageName)
            else if (hasTypeLevelEventSourcedEntitySubs) {
              val kalixSubscriptionMethods =
                methodsForTypeLevelESSubscriptions(tableUpdater, tableName, tableProtoMessageName)
              combineBy("ES", kalixSubscriptionMethods, messageCodec, tableUpdater)
            } else if (hasTypeLevelTopicSubs) {
              val kalixSubscriptionMethods =
                methodsForTypeLevelTopicSubscriptions(tableUpdater, tableName, tableProtoMessageName)
              combineBy("Topic", kalixSubscriptionMethods, messageCodec, tableUpdater)
            } else if (hasTypeLevelStreamSubs) {
              val kalixSubscriptionMethods =
                methodsForTypeLevelStreamSubscriptions(tableUpdater, tableName, tableProtoMessageName)
              combineBy("Stream", kalixSubscriptionMethods, messageCodec, tableUpdater)
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

    val kalixMethods: Seq[KalixMethod] = allQueryMethods.map(_.queryMethod) ++ updateMethods
    val serviceName = nameGenerator.getName(component.getSimpleName)
    val additionalMessages =
      tableTypeDescriptors.toSet ++ allQueryMethods.map(_.queryOutputSchemaDescriptor) ++ allQueryMethods.flatMap(
        _.queryInputSchemaDescriptor.toSet)

    val serviceLevelOptions = {
      val forMerge = Seq(
        AclDescriptorFactory.serviceLevelAclAnnotation(component, default = Some(AclDescriptorFactory.denyAll)),
        JwtDescriptorFactory.serviceLevelJwtAnnotation(component),
        // FIXME does these two do anything anymore - no annotations on View itself
        eventingInForEventSourcedEntityServiceLevel(component),
        eventingInForTopicServiceLevel(component)) ++ tableUpdaters.map(subscribeToEventStream)
      mergeServiceOptions(forMerge: _*)
    }

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
      queryOutputSchemaDescriptor: ProtoMessageDescriptors,
      queryString: String)

  private def queryMethods(component: Class[_]): Seq[QueryMethod] = {
    // we only take methods with Query annotations
    val annotatedQueryMethods =
      component.getDeclaredMethods.toIndexedSeq
        .filter(m => m.getAnnotation(classOf[Query]) != null && m.getReturnType == classOf[View.QueryEffect[_]])

    annotatedQueryMethods.map { queryMethod =>
      val queryOutputType = queryMethod.getGenericReturnType
        .asInstanceOf[java.lang.reflect.ParameterizedType]
        .getActualTypeArguments
        .head
        .asInstanceOf[Class[_]]

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

      QueryMethod(kalixQueryMethod, QueryParametersSchemaDescriptor, queryOutputSchemaDescriptor, queryStr)
    }
  }

  private def methodsForTypeLevelStreamSubscriptions(
      tableUpdater: Class[_],
      tableName: String,
      tableProtoMessageName: String): Map[String, Seq[KalixMethod]] = {
    val methods = eligibleSubscriptionMethods(tableUpdater, tableName, tableProtoMessageName, None).toIndexedSeq
    val ann = tableUpdater.getAnnotation(classOf[FromServiceStream])
    val key = ann.id().capitalize
    Map(key -> methods)
  }

  private def methodsForTypeLevelESSubscriptions(
      tableUpdater: Class[_],
      tableName: String,
      tableProtoMessageName: String): Map[String, Seq[KalixMethod]] = {

    val methods = eligibleSubscriptionMethods(
      tableUpdater,
      tableName,
      tableProtoMessageName,
      Some(eventingInForEventSourcedEntity(tableUpdater))).toIndexedSeq
    val entityType = findEventSourcedEntityType(tableUpdater)
    Map(entityType -> methods)
  }

  private def methodsForTypeLevelTopicSubscriptions(
      tableUpdater: Class[_],
      tableName: String,
      tableProtoMessageName: String): Map[String, Seq[KalixMethod]] = {

    val methods = eligibleSubscriptionMethods(
      tableUpdater,
      tableName,
      tableProtoMessageName,
      Some(eventingInForTopic(tableUpdater))).toIndexedSeq
    val entityType = findSubscriptionTopicName(tableUpdater)
    Map(entityType -> methods)
  }

  private def eligibleSubscriptionMethods(
      tableUpdater: Class[_],
      tableName: String,
      tableProtoMessageName: String,
      eventing: Option[Eventing]) =
    tableUpdater.getMethods.filter(hasUpdateEffectOutput).map { method =>
      // event sourced or topic subscription updates
      val methodOptionsBuilder = kalix.MethodOptions.newBuilder()

      eventing.foreach(methodOptionsBuilder.setEventing)

      addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, true)

      KalixMethod(SubscriptionServiceMethod(method))
        .withKalixOptions(methodOptionsBuilder.build())
    }

  private def subscriptionForTypeLevelValueEntity(
      tableUpdater: Class[_],
      tableType: Class[_],
      tableName: String,
      tableProtoMessageName: String) = {

    val methodOptionsBuilder = kalix.MethodOptions.newBuilder()

    methodOptionsBuilder.setEventing(eventingInForValueEntity(tableUpdater, handleDeletes = false))

    val subscriptionVEType = tableUpdater
      .getAnnotation(classOf[FromKeyValueEntity])
      .value()
      .getGenericSuperclass
      .asInstanceOf[ParameterizedType]
      .getActualTypeArguments
      .head
      .asInstanceOf[Class[_]]

    val transform = subscriptionVEType != tableType

    addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, transform)
    val kalixOptions = methodOptionsBuilder.build()

    if (transform) {
      import Reflect.methodOrdering
      val handleDeletesMethods = tableUpdater.getMethods
        .filter(hasHandleDeletes)
        .sorted
        .map { method =>
          val methodOptionsBuilder = kalix.MethodOptions.newBuilder()
          methodOptionsBuilder.setEventing(eventingInForValueEntity(tableUpdater, handleDeletes = true))
          addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, transform)

          KalixMethod(HandleDeletesServiceMethod(method))
            .withKalixOptions(methodOptionsBuilder.build())
            .withKalixOptions(buildJWTOptions(method))
        }

      val valueEntitySubscriptionMethods = tableUpdater.getMethods
        .filterNot(hasHandleDeletes)
        .filter(hasUpdateEffectOutput)
        .sorted // make sure we get the methods in deterministic order
        .map { method =>

          val methodOptionsBuilder = kalix.MethodOptions.newBuilder()
          methodOptionsBuilder.setEventing(eventingInForValueEntity(tableUpdater, handleDeletes = false))
          addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, transform)

          KalixMethod(SubscriptionServiceMethod(method))
            .withKalixOptions(methodOptionsBuilder.build())
            .withKalixOptions(buildJWTOptions(method))
        }

      (handleDeletesMethods ++ valueEntitySubscriptionMethods).toSeq
    } else {
      //TODO verify if virtual methods are needed right now, there is no need for the runtime<->sdk round trip optimisation
      if (findHandleDeletes(tableUpdater)) {
        val deleteMethodOptionsBuilder = kalix.MethodOptions.newBuilder()
        deleteMethodOptionsBuilder.setEventing(eventingInForValueEntity(tableUpdater, handleDeletes = true))
        addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, deleteMethodOptionsBuilder, transform)
        Seq(
          KalixMethod(VirtualServiceMethod(tableUpdater, "OnChange", tableType)).withKalixOptions(kalixOptions),
          KalixMethod(VirtualDeleteServiceMethod(tableUpdater, "OnDelete")).withKalixOptions(
            deleteMethodOptionsBuilder.build()))
      } else {
        Seq(KalixMethod(VirtualServiceMethod(tableUpdater, "OnChange", tableType)).withKalixOptions(kalixOptions))
      }
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
