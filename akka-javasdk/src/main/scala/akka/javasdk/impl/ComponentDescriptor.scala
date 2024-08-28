/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.reflection.ActionHandlerMethod
import akka.javasdk.impl.reflection.AnyJsonRequestServiceMethod
import akka.javasdk.impl.reflection.CombinedSubscriptionServiceMethod
import akka.javasdk.impl.reflection.CommandHandlerMethod
import akka.javasdk.impl.reflection.DeleteServiceMethod
import akka.javasdk.impl.reflection.ExtractorCreator
import akka.javasdk.impl.reflection.KalixMethod
import akka.javasdk.impl.reflection.NameGenerator
import akka.javasdk.impl.reflection.ParameterExtractor
import akka.javasdk.impl.reflection.ParameterExtractors
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.reflection.ServiceMethod
import akka.javasdk.impl.reflection.SubscriptionServiceMethod
import akka.javasdk.impl.reflection.VirtualServiceMethod
import java.lang.reflect.ParameterizedType
import AnySupport.ProtobufEmptyTypeUrl
import akka.javasdk.annotations.ComponentId
import com.google.api.AnnotationsProto
import com.google.api.HttpRule
import com.google.protobuf.BytesValue
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto
import com.google.protobuf.DescriptorProtos.MethodOptions
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto
import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.Empty
import com.google.protobuf.{ Any => JavaPbAny }

/**
 * The component descriptor is both used for generating the protobuf service descriptor to communicate the service type
 * and methods etc. to Kalix and for the reflective routers routing incoming calls to the right method of the user
 * component class.
 */
private[akka] object ComponentDescriptor {

  def descriptorFor(component: Class[_], messageCodec: JsonMessageCodec): ComponentDescriptor =
    ComponentDescriptorFactory.getFactoryFor(component).buildDescriptorFor(component, messageCodec, new NameGenerator)

  def apply(
      nameGenerator: NameGenerator,
      messageCodec: JsonMessageCodec,
      serviceName: String,
      serviceOptions: Option[kalix.ServiceOptions],
      packageName: String,
      kalixMethods: Seq[KalixMethod],
      additionalMessages: Seq[ProtoMessageDescriptors] = Nil): ComponentDescriptor = {

    val otherMessageProtos: Seq[DescriptorProtos.DescriptorProto] =
      additionalMessages.flatMap(pm => pm.mainMessageDescriptor +: pm.additionalMessageDescriptors)

    val grpcService = ServiceDescriptorProto.newBuilder()
    grpcService.setName(serviceName)

    serviceOptions.foreach { serviceOpts =>
      val options =
        DescriptorProtos.ServiceOptions
          .newBuilder()
          .setExtension(kalix.Annotations.service, serviceOpts)
          .build()
      grpcService.setOptions(options)
    }

    def methodToNamedComponentMethod(kalixMethod: KalixMethod): NamedComponentMethod = {

      kalixMethod.validate()

      val (inputMessageName: String, extractors: Map[Int, ExtractorCreator], inputProto: Option[DescriptorProto]) =
        kalixMethod.serviceMethod match {
          case serviceMethod: CommandHandlerMethod =>
            val (inputProto, extractors) =
              buildCommandHandlerMessageAndExtractors(nameGenerator, serviceMethod)
            (inputProto.getName, extractors, Some(inputProto))

          case actionHandlerMethod: ActionHandlerMethod =>
            val (inputProto, extractors) =
              buildActionHandlerMessageAndExtractors(nameGenerator, actionHandlerMethod)
            (inputProto.getName, extractors, Some(inputProto))

          case anyJson: AnyJsonRequestServiceMethod =>
            if (anyJson.inputType == classOf[Array[Byte]]) {
              (BytesValue.getDescriptor.getFullName, Map.empty[Int, ExtractorCreator], None)
            } else {
              (JavaPbAny.getDescriptor.getFullName, Map.empty[Int, ExtractorCreator], None)
            }

          case _: DeleteServiceMethod =>
            (Empty.getDescriptor.getFullName, Map.empty[Int, ExtractorCreator], None)
        }

      val grpcMethodName = nameGenerator.getName(kalixMethod.serviceMethod.methodName.capitalize)
      val grpcMethodBuilder =
        buildGrpcMethod(
          grpcMethodName,
          inputMessageName,
          outputTypeName(kalixMethod),
          kalixMethod.serviceMethod.streamIn,
          kalixMethod.serviceMethod.streamOut)

      grpcMethodBuilder.setOptions(createMethodOptions(kalixMethod))

      val grpcMethod = grpcMethodBuilder.build()
      grpcService.addMethod(grpcMethod)

      NamedComponentMethod(
        kalixMethod.serviceMethod,
        messageCodec,
        grpcMethodName,
        extractors,
        inputMessageName,
        inputProto)
    }

    val namedMethods: Seq[NamedComponentMethod] = kalixMethods.map(methodToNamedComponentMethod)
    val inputMessageProtos: Set[DescriptorProtos.DescriptorProto] = namedMethods.flatMap(_.inputProto).toSet

    val fileDescriptor: Descriptors.FileDescriptor =
      ProtoDescriptorGenerator.genFileDescriptor(
        serviceName,
        packageName,
        grpcService.build(),
        inputMessageProtos ++ otherMessageProtos)

    val methods: Map[String, CommandHandler] =
      namedMethods.map { method => (method.grpcMethodName, method.toCommandHandler(fileDescriptor)) }.toMap

    val serviceDescriptor: Descriptors.ServiceDescriptor =
      fileDescriptor.findServiceByName(grpcService.getName)

    new ComponentDescriptor(serviceName, packageName, methods, serviceDescriptor, fileDescriptor)
  }

  private def outputTypeName(kalixMethod: KalixMethod): String = {
    kalixMethod.serviceMethod.javaMethodOpt match {
      case Some(javaMethod) =>
        javaMethod.getGenericReturnType match {
          case parameterizedType: ParameterizedType =>
            val outputType = parameterizedType.getActualTypeArguments.head
            if (outputType == classOf[Array[Byte]]) {
              BytesValue.getDescriptor.getFullName
            } else {
              JavaPbAny.getDescriptor.getFullName
            }
          case _ => JavaPbAny.getDescriptor.getFullName
        }
      case None => JavaPbAny.getDescriptor.getFullName
    }
  }

  private def createMethodOptions(kalixMethod: KalixMethod): MethodOptions = {

    val methodOptions = MethodOptions.newBuilder()

    kalixMethod.serviceMethod match {
      case commandHandlerMethod: CommandHandlerMethod =>
        val httpRuleBuilder = buildHttpRule(commandHandlerMethod)

        if (commandHandlerMethod.hasInputType) httpRuleBuilder.setBody("json_body")

        methodOptions.setExtension(AnnotationsProto.http, httpRuleBuilder.build())

      case _ => //ignore
    }

    kalixMethod.methodOptions.foreach(option => methodOptions.setExtension(kalix.Annotations.method, option))
    methodOptions.build()
  }

  // intermediate format that references input message by name
  // once we have built the full file descriptor, we can look up for the input message using its name
  private case class NamedComponentMethod(
      serviceMethod: ServiceMethod,
      messageCodec: JsonMessageCodec,
      grpcMethodName: String,
      extractorCreators: Map[Int, ExtractorCreator],
      inputMessageName: String,
      inputProto: Option[DescriptorProto]) {

    type ParameterExtractorsArray = Array[ParameterExtractor[InvocationContext, AnyRef]]

    def toCommandHandler(fileDescriptor: FileDescriptor): CommandHandler = {
      serviceMethod match {
        case method: CommandHandlerMethod =>
          val messageDescriptor = fileDescriptor.findMessageTypeByName(inputMessageName)
          // CommandHandler request always have proto messages as input,
          // their type url are prefixed by DefaultTypeUrlPrefix
          // It's possible for a user to configure another prefix, but this is done through the Kalix instance
          // and the Java SDK doesn't expose it.
          val typeUrl = AnySupport.DefaultTypeUrlPrefix + "/" + messageDescriptor.getFullName
          val methodInvokers =
            serviceMethod.javaMethodOpt
              .map { meth =>
                val parameterExtractors: ParameterExtractorsArray = {
                  meth.getParameterTypes.length match {
                    case 1 =>
                      Array(
                        new ParameterExtractors.BodyExtractor(messageDescriptor.findFieldByNumber(1), method.inputType))
                    case 0 =>
                      // parameterless method, not extractor needed
                      Array.empty
                    case n =>
                      throw new IllegalStateException(
                        s"Command handler ${method} is expecting $n parameters, should be 0 or 1")
                  }
                }
                Map(typeUrl -> MethodInvoker(meth, parameterExtractors))
              }
              .getOrElse(Map.empty)

          CommandHandler(grpcMethodName, messageCodec, messageDescriptor, methodInvokers)

        case method: CombinedSubscriptionServiceMethod =>
          val methodInvokers =
            method.methodsMap.map { case (typeUrl, meth) =>
              val parameterExtractors: ParameterExtractorsArray = {
                meth.getParameterTypes.length match {
                  case 1 =>
                    Array(new ParameterExtractors.AnyBodyExtractor[AnyRef](meth.getParameterTypes.head, messageCodec))
                  case n =>
                    throw new IllegalStateException(
                      s"Update handler ${method} is expecting $n parameters, should be 1, the update")
                }
              }

              (typeUrl, MethodInvoker(meth, parameterExtractors))
            }

          CommandHandler(grpcMethodName, messageCodec, JavaPbAny.getDescriptor, methodInvokers)

        case method: SubscriptionServiceMethod =>
          val methodInvokers =
            serviceMethod.javaMethodOpt
              .map { meth =>

                val parameterExtractors: ParameterExtractorsArray =
                  Array(ParameterExtractors.AnyBodyExtractor(method.inputType, messageCodec))

                val typeUrls = messageCodec.typeUrlsFor(method.inputType)
                typeUrls.map(_ -> MethodInvoker(meth, parameterExtractors)).toMap
              }
              .getOrElse(Map.empty)

          CommandHandler(grpcMethodName, messageCodec, JavaPbAny.getDescriptor, methodInvokers)

        case _: VirtualServiceMethod =>
          //java method is empty
          CommandHandler(grpcMethodName, messageCodec, JavaPbAny.getDescriptor, Map.empty)

        case _: DeleteServiceMethod =>
          val methodInvokers = serviceMethod.javaMethodOpt.map { meth =>
            (ProtobufEmptyTypeUrl, MethodInvoker(meth, Array.empty[ParameterExtractor[InvocationContext, AnyRef]]))
          }.toMap

          CommandHandler(grpcMethodName, messageCodec, Empty.getDescriptor, methodInvokers)

        case method: ActionHandlerMethod =>
          val messageDescriptor = fileDescriptor.findMessageTypeByName(inputMessageName)
          // Action handler request always have proto messages as input,
          // their type url are prefixed by DefaultTypeUrlPrefix
          // It's possible for a user to configure another prefix, but this is done through the Kalix instance
          // and the Java SDK doesn't expose it.
          val typeUrl = AnySupport.DefaultTypeUrlPrefix + "/" + messageDescriptor.getFullName
          val methodInvokers =
            serviceMethod.javaMethodOpt
              .map { meth =>
                val parameterExtractors: ParameterExtractorsArray =
                  if (meth.getParameterTypes.length == 1)
                    Array(
                      new ParameterExtractors.BodyExtractor(messageDescriptor.findFieldByNumber(1), method.inputType))
                  else
                    Array.empty // parameterless method, not extractor needed

                Map(typeUrl -> MethodInvoker(meth, parameterExtractors))
              }
              .getOrElse(Map.empty)

          CommandHandler(grpcMethodName, messageCodec, messageDescriptor, methodInvokers)
      }

    }
  }

  private def buildActionHandlerMessageAndExtractors(
      nameGenerator: NameGenerator,
      actionHandlerMethod: ActionHandlerMethod): (DescriptorProto, Map[Int, ExtractorCreator]) = {
    val inputMessageName = nameGenerator.getName(actionHandlerMethod.methodName.capitalize + "KalixSyntheticRequest")

    val inputMessageDescriptor = DescriptorProto.newBuilder()
    inputMessageDescriptor.setName(inputMessageName)

    if (actionHandlerMethod.hasInputType) {
      val bodyFieldDesc = FieldDescriptorProto
        .newBuilder()
        // todo ensure this is unique among field names
        .setName("json_body")
        // Always put the body at position 1 - even if there's no body, leave position 1 free. This keeps the body
        // parameter stable in case the user adds a body.
        .setNumber(1)
        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
        .setTypeName("google.protobuf.Any")
        .build()

      inputMessageDescriptor.addField(bodyFieldDesc)
    }
    (inputMessageDescriptor.build(), Map.empty)
  }

  private def buildCommandHandlerMessageAndExtractors(
      nameGenerator: NameGenerator,
      commandHandlerMethod: CommandHandlerMethod): (DescriptorProto, Map[Int, ExtractorCreator]) = {

    val inputMessageName = nameGenerator.getName(commandHandlerMethod.methodName.capitalize + "KalixSyntheticRequest")

    val inputMessageDescriptor = DescriptorProto.newBuilder()
    inputMessageDescriptor.setName(inputMessageName)

    if (commandHandlerMethod.hasInputType) {
      val bodyFieldDesc = FieldDescriptorProto
        .newBuilder()
        // todo ensure this is unique among field names
        .setName("json_body")
        // Always put the body at position 1 - even if there's no body, leave position 1 free. This keeps the body
        // parameter stable in case the user adds a body.
        .setNumber(1)
        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
        .setTypeName("google.protobuf.Any")
        .build()

      inputMessageDescriptor.addField(bodyFieldDesc)
    }

    val idFieldDesc = FieldDescriptorProto
      .newBuilder()
      .setName("id")
      // id always go on position 2 after the body
      .setNumber(2)
      .setType(FieldDescriptorProto.Type.TYPE_STRING)
      .setOptions {
        DescriptorProtos.FieldOptions
          .newBuilder()
          .setExtension(kalix.Annotations.field, kalix.FieldOptions.newBuilder().setId(true).build())
          .build()
      }
      .build()

    inputMessageDescriptor.addField(idFieldDesc)
    (inputMessageDescriptor.build(), Map.empty)
  }

  private def buildHttpRule(commandHandlerMethod: CommandHandlerMethod): HttpRule.Builder = {
    val httpRule = HttpRule.newBuilder()

    val componentTypeId =
      if (Reflect.isView(commandHandlerMethod.component)) {
        commandHandlerMethod.component.getAnnotation(classOf[ComponentId]).value()
      } else if (Reflect.isAction(commandHandlerMethod.component)) {
        val annotation = commandHandlerMethod.component.getAnnotation(classOf[ComponentId])
        // don't require id on actions (subscriptions etc)
        if (annotation == null) commandHandlerMethod.getClass.getName
        else annotation.value()
      } else {
        commandHandlerMethod.component.getAnnotation(classOf[ComponentId]).value()
      }

    val urlTemplate = commandHandlerMethod.urlTemplate.templateUrl(componentTypeId, commandHandlerMethod.method.getName)
    if (commandHandlerMethod.hasInputType)
      httpRule.setPost(urlTemplate)
    else
      httpRule.setGet(urlTemplate)

  }

  private def buildGrpcMethod(
      grpcMethodName: String,
      inputTypeName: String,
      outputTypeName: String,
      streamIn: Boolean,
      streamOut: Boolean): MethodDescriptorProto.Builder =
    MethodDescriptorProto
      .newBuilder()
      .setName(grpcMethodName)
      .setInputType(inputTypeName)
      .setClientStreaming(streamIn)
      .setServerStreaming(streamOut)
      .setOutputType(outputTypeName)

}

private[akka] final case class ComponentDescriptor private (
    serviceName: String,
    packageName: String,
    commandHandlers: Map[String, CommandHandler],
    serviceDescriptor: Descriptors.ServiceDescriptor,
    fileDescriptor: Descriptors.FileDescriptor)
