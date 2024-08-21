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
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasViewUpdateEffectOutput
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
import akka.platform.javasdk.view.View

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
            def hasMethodLevelEventSourcedEntitySubs = tableUpdater.getMethods.exists(hasEventSourcedEntitySubscription)

            def hasTypeLevelEventSourcedEntitySubs = hasEventSourcedEntitySubscription(tableUpdater)

            def hasTypeLevelValueEntitySubs = hasValueEntitySubscription(tableUpdater)

            def hasMethodLevelValueEntitySubs = tableUpdater.getMethods.exists(hasValueEntitySubscription)

            def hasTypeLevelTopicSubs = hasTopicSubscription(tableUpdater)

            def hasMethodLevelTopicSubs = tableUpdater.getMethods.exists(hasTopicSubscription)

            def hasTypeLevelStreamSubs = hasStreamSubscription(tableUpdater)

            if (hasTypeLevelValueEntitySubs)
              subscriptionForTypeLevelValueEntity(tableUpdater, tableType, tableName, tableProtoMessageName)
            else if (hasMethodLevelValueEntitySubs)
              subscriptionForMethodLevelValueEntity(tableUpdater, tableName, tableProtoMessageName)
            else if (hasTypeLevelEventSourcedEntitySubs) {
              val kalixSubscriptionMethods =
                methodsForTypeLevelESSubscriptions(tableUpdater, tableName, tableProtoMessageName)
              combineBy("ES", kalixSubscriptionMethods, messageCodec, tableUpdater)
            } else if (hasMethodLevelEventSourcedEntitySubs) {
              val methodsForMethodLevelESSubscriptions =
                subscriptionEventSourcedEntityMethodLevel(tableUpdater, tableName, tableProtoMessageName)
              combineByES(methodsForMethodLevelESSubscriptions, messageCodec, tableUpdater)
            } else if (hasTypeLevelTopicSubs) {
              val kalixSubscriptionMethods =
                methodsForTypeLevelTopicSubscriptions(tableUpdater, tableName, tableProtoMessageName)
              combineBy("Topic", kalixSubscriptionMethods, messageCodec, tableUpdater)
            } else if (hasMethodLevelTopicSubs) {
              val methodsForMethodLevelTopicSubscriptions =
                subscriptionTopicMethodLevel(tableUpdater, tableName, tableProtoMessageName)
              combineByTopic(methodsForMethodLevelTopicSubscriptions, messageCodec, tableUpdater)
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
    tableUpdater.getMethods.filter(hasViewUpdateEffectOutput).map { method =>
      // event sourced or topic subscription updates
      val methodOptionsBuilder = kalix.MethodOptions.newBuilder()

      eventing.foreach(methodOptionsBuilder.setEventing)

      addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, true)

      KalixMethod(SubscriptionServiceMethod(method))
        .withKalixOptions(methodOptionsBuilder.build())
    }

  private def subscriptionEventSourcedEntityMethodLevel(
      tableUpdater: Class[_],
      tableName: String,
      tableProtoMessageName: String): Seq[KalixMethod] = {

    def getMethodsWithSubscription(tableUpdater: Class[_]): Seq[Method] = {
      import akka.platform.javasdk.impl.reflection.Reflect.methodOrdering
      tableUpdater.getMethods
        .filter(hasEventSourcedEntitySubscription)
        .sorted
        .toIndexedSeq
    }

    def getEventing(method: Method, component: Class[_]): Eventing =
      if (hasEventSourcedEntitySubscription(component)) eventingInForEventSourcedEntity(component)
      else eventingInForEventSourcedEntity(method)

    getMethodsWithSubscription(tableUpdater).map { method =>
      // event sourced or topic subscription updates
      val methodOptionsBuilder = kalix.MethodOptions.newBuilder()

      if (hasEventSourcedEntitySubscription(method))
        methodOptionsBuilder.setEventing(getEventing(method, tableUpdater))

      addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, true)

      KalixMethod(SubscriptionServiceMethod(method))
        .withKalixOptions(methodOptionsBuilder.build())
    }
  }

  private def subscriptionTopicMethodLevel(
      tableUpdater: Class[_],
      tableName: String,
      tableProtoMessageName: String): Seq[KalixMethod] = {

    def getMethodsWithSubscription(tableUpdater: Class[_]): Seq[Method] = {
      import Reflect.methodOrdering
      tableUpdater.getMethods
        .filter(hasTopicSubscription)
        .sorted
        .toIndexedSeq
    }

    getMethodsWithSubscription(tableUpdater).map { method =>
      // event sourced or topic subscription updates
      val methodOptionsBuilder = kalix.MethodOptions.newBuilder()

      methodOptionsBuilder.setEventing(eventingInForTopic(method))

      addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, true)

      KalixMethod(SubscriptionServiceMethod(method))
        .withKalixOptions(methodOptionsBuilder.build())
    }
  }

  private def subscriptionForMethodLevelValueEntity(
      tableUpdater: Class[_],
      tableName: String,
      tableProtoMessageName: String): Seq[KalixMethod] = {

    import Reflect.methodOrdering

    val handleDeletesMethods = tableUpdater.getMethods
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

    val valueEntitySubscriptionMethods = tableUpdater.getMethods
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
      tableUpdater: Class[_],
      tableType: Class[_],
      tableName: String,
      tableProtoMessageName: String) = {
    // create a virtual method
    val methodOptionsBuilder = kalix.MethodOptions.newBuilder()

    val entityType = findValueEntityType(tableUpdater)
    methodOptionsBuilder.setEventing(eventingInForValueEntity(entityType, handleDeletes = false))

    addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, methodOptionsBuilder, transform = false)
    val kalixOptions = methodOptionsBuilder.build()

    if (findHandleDeletes(tableUpdater)) {
      val deleteMethodOptionsBuilder = kalix.MethodOptions.newBuilder()
      deleteMethodOptionsBuilder.setEventing(eventingInForValueEntity(entityType, handleDeletes = true))
      addTableOptionsToUpdateMethod(tableName, tableProtoMessageName, deleteMethodOptionsBuilder, transform = false)
      Seq(
        KalixMethod(VirtualServiceMethod(tableUpdater, "OnChange", tableType)).withKalixOptions(kalixOptions),
        KalixMethod(VirtualDeleteServiceMethod(tableUpdater, "OnDelete")).withKalixOptions(
          deleteMethodOptionsBuilder.build()))
    } else {
      Seq(KalixMethod(VirtualServiceMethod(tableUpdater, "OnChange", tableType)).withKalixOptions(kalixOptions))
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
