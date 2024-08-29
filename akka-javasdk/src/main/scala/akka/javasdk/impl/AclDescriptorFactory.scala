/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.annotations.Acl

import java.lang.reflect.Method
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import kalix.PrincipalMatcher
import kalix.{ Acl => ProtoAcl }
import kalix.{ Annotations => KalixAnnotations }
import org.slf4j.LoggerFactory

import java.util.Collections
import scala.PartialFunction.condOpt

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object AclDescriptorFactory {

  private val logger = LoggerFactory.getLogger(classOf[AclDescriptorFactory.type])

  val invalidAnnotationUsage: String =
    "Invalid annotation usage. Matcher has both 'principal' and 'service' defined. " +
    "Only one is allowed."

  def validateMatcher(matcher: Acl.Matcher): Unit = {
    if (matcher.principal() != Acl.Principal.UNSPECIFIED && matcher.service().nonEmpty)
      throw new IllegalArgumentException(invalidAnnotationUsage)
  }

  val denyAll: ProtoAcl =
    ProtoAcl.newBuilder().addAllAllow(Collections.emptyList()).build()

  private def deriveProtoAnnotation(aclJavaAnnotation: Acl): ProtoAcl = {

    aclJavaAnnotation.allow().foreach(matcher => validateMatcher(matcher))
    aclJavaAnnotation.deny().foreach(matcher => validateMatcher(matcher))

    val aclBuilder = ProtoAcl.newBuilder()

    aclJavaAnnotation.allow.zipWithIndex.foreach { case (allow, idx) =>
      val principalMatcher = PrincipalMatcher.newBuilder()
      allow.principal match {
        case Acl.Principal.ALL =>
          principalMatcher.setPrincipal(PrincipalMatcher.Principal.ALL)
        case Acl.Principal.INTERNET =>
          principalMatcher.setPrincipal(PrincipalMatcher.Principal.INTERNET)
        case Acl.Principal.UNSPECIFIED =>
          principalMatcher.setService(allow.service())
      }

      aclBuilder.addAllow(idx, principalMatcher)
    }

    aclJavaAnnotation.deny.zipWithIndex.foreach { case (deny, idx) =>
      val principalMatcher = PrincipalMatcher.newBuilder()
      deny.principal match {
        case Acl.Principal.ALL =>
          principalMatcher.setPrincipal(PrincipalMatcher.Principal.ALL)
        case Acl.Principal.INTERNET =>
          principalMatcher.setPrincipal(PrincipalMatcher.Principal.INTERNET)
        case Acl.Principal.UNSPECIFIED =>
          principalMatcher.setService(deny.service())

      }
      aclBuilder.addDeny(idx, principalMatcher)
    }

    if (aclJavaAnnotation.inheritDenyCode()) {
      aclBuilder.setDenyCode(0)
    } else {
      aclBuilder.setDenyCode(aclJavaAnnotation.denyCode().value)
    }

    aclBuilder.build()
  }

  def defaultAclFileDescriptor: DescriptorProtos.FileDescriptorProto =
    buildAclFileDescriptor(denyAll) // deny all by default

  def buildAclFileDescriptor(cls: Class[_]): DescriptorProtos.FileDescriptorProto =
    if (cls.getAnnotation(classOf[Acl]) != null)
      buildAclFileDescriptor(deriveProtoAnnotation(cls.getAnnotation(classOf[Acl])))
    else
      defaultAclFileDescriptor

  private def buildAclFileDescriptor(acl: ProtoAcl): DescriptorProtos.FileDescriptorProto = {
    // do we need to recurse into the dependencies of the dependencies? Probably not, just top level imports.
    val dependencies: Array[Descriptors.FileDescriptor] = Array(KalixAnnotations.getDescriptor)

    val policyFile = "kalix_policy.proto"

    val protoBuilder =
      DescriptorProtos.FileDescriptorProto.newBuilder
        .setName(policyFile)
        .setSyntax("proto3")
        .setPackage("akka.javasdk")

    val kalixFileOptions = kalix.FileOptions.newBuilder
    kalixFileOptions.setAcl(acl)

    val options =
      DescriptorProtos.FileOptions
        .newBuilder()
        .setExtension(kalix.Annotations.file, kalixFileOptions.build())
        .build()

    protoBuilder.setOptions(options)
    val fdProto = protoBuilder.build
    val fd = Descriptors.FileDescriptor.buildFrom(fdProto, dependencies)
    if (logger.isDebugEnabled) {
      logger.debug("Generated file descriptor for service [{}]: \n{}", policyFile, ProtoDescriptorRenderer.toString(fd))
    }
    fd.toProto
  }

  def serviceLevelAclAnnotation(component: Class[_], default: Option[ProtoAcl] = None): Option[kalix.ServiceOptions] = {

    val javaAclAnnotation = component.getAnnotation(classOf[Acl])

    def buildServiceOpts(acl: ProtoAcl): kalix.ServiceOptions = {
      kalix.ServiceOptions
        .newBuilder()
        .setAcl(acl)
        .build()
    }

    condOpt(javaAclAnnotation, default) {
      case (aclAnnotation, _) if aclAnnotation != null => buildServiceOpts(deriveProtoAnnotation(aclAnnotation))
      case (null, Some(acl))                           => buildServiceOpts(acl)
    }
  }

  def methodLevelAclAnnotation(method: Method): Option[kalix.MethodOptions] = {

    val javaAclAnnotation = method.getAnnotation(classOf[Acl])

    Option.when(javaAclAnnotation != null) {
      val kalixMethodOptions = kalix.MethodOptions.newBuilder()
      kalixMethodOptions.setAcl(deriveProtoAnnotation(javaAclAnnotation))
      kalixMethodOptions.build()
    }
  }

}
