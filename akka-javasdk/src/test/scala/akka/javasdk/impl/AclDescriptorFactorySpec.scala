/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.annotations.Acl
import akka.javasdk.impl.AclDescriptorFactory

import scala.reflect.ClassTag
import kalix.FileOptions
import kalix.PrincipalMatcher
import akka.javasdk.testmodels.AclTestModels.MainAllowAllServices
import akka.javasdk.testmodels.AclTestModels.MainAllowListOfServices
import akka.javasdk.testmodels.AclTestModels.MainAllowPrincipalAll
import akka.javasdk.testmodels.AclTestModels.MainAllowPrincipalInternet
import akka.javasdk.testmodels.AclTestModels.MainDenyAllServices
import akka.javasdk.testmodels.AclTestModels.MainDenyListOfServices
import akka.javasdk.testmodels.AclTestModels.MainDenyPrincipalAll
import akka.javasdk.testmodels.AclTestModels.MainDenyPrincipalInternet
import akka.javasdk.testmodels.AclTestModels.MainDenyWithCode
import akka.javasdk.testmodels.AclTestModels.MainWithInvalidAllowAnnotation
import akka.javasdk.testmodels.AclTestModels.MainWithInvalidDenyAnnotation
import akka.javasdk.testmodels.AclTestModels.MainWithoutAnnotation
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AclDescriptorFactorySpec extends AnyWordSpec with Matchers {

  def lookupExtension[T: ClassTag]: FileOptions =
    AclDescriptorFactory
      .buildAclFileDescriptor(implicitly[ClassTag[T]].runtimeClass)
      .getOptions
      .getExtension(kalix.Annotations.file)

  "AclDescriptorFactory.defaultAclFileDescriptor" should {

    "generate an empty descriptor if no ACL annotation is found" in {
      val extension = lookupExtension[MainWithoutAnnotation]
      val principals = extension.getAcl.getAllowList
      principals shouldBe empty
    }

    "generate a default ACL file descriptor with deny code" in {
      val extension = lookupExtension[MainDenyWithCode]
      val denyCode = extension.getAcl.getDenyCode
      denyCode shouldBe Acl.DenyStatusCode.CONFLICT.value
    }

    "generate a default ACL file descriptor with allow all services" in {
      val extension = lookupExtension[MainAllowAllServices]
      val service = extension.getAcl.getAllow(0).getService
      service shouldBe "*"
    }

    "generate a default ACL file descriptor with allow two services" in {
      val extension = lookupExtension[MainAllowListOfServices]
      val service1 = extension.getAcl.getAllow(0).getService
      service1 shouldBe "foo"

      val service2 = extension.getAcl.getAllow(1).getService
      service2 shouldBe "bar"
    }

    "generate a default ACL file descriptor with allow Principal INTERNET" in {
      val extension = lookupExtension[MainAllowPrincipalInternet]
      val principal = extension.getAcl.getAllow(0).getPrincipal
      principal shouldBe PrincipalMatcher.Principal.INTERNET
    }

    "generate a default ACL file descriptor with allow Principal ALL" in {
      val extension = lookupExtension[MainAllowPrincipalAll]
      val principal = extension.getAcl.getAllow(0).getPrincipal
      principal shouldBe PrincipalMatcher.Principal.ALL
    }

    "fail if both Principal and Service are defined" in {
      intercept[IllegalArgumentException] {
        lookupExtension[MainWithInvalidAllowAnnotation]
      }.getMessage shouldBe AclDescriptorFactory.invalidAnnotationUsage
    }

    "generate a default ACL file descriptor with deny all services" in {
      val extension = lookupExtension[MainDenyAllServices]
      val service = extension.getAcl.getDeny(0).getService
      service shouldBe "*"
    }

    "generate a default ACL file descriptor with deny two services" in {
      val extension = lookupExtension[MainDenyListOfServices]
      val service1 = extension.getAcl.getDeny(0).getService
      service1 shouldBe "foo"

      val service2 = extension.getAcl.getDeny(1).getService
      service2 shouldBe "bar"
    }

    "generate a default ACL file descriptor with deny Principal INTERNET" in {
      val extension = lookupExtension[MainDenyPrincipalInternet]
      val principal = extension.getAcl.getDeny(0).getPrincipal
      principal shouldBe PrincipalMatcher.Principal.INTERNET
    }

    "generate a default ACL file descriptor with deny Principal ALL" in {
      val extension = lookupExtension[MainDenyPrincipalAll]
      val principal = extension.getAcl.getDeny(0).getPrincipal
      principal shouldBe PrincipalMatcher.Principal.ALL
    }

    "fail if both Principal and Service are defined in 'deny' field" in {
      intercept[IllegalArgumentException] {
        lookupExtension[MainWithInvalidDenyAnnotation]
      }.getMessage shouldBe AclDescriptorFactory.invalidAnnotationUsage
    }
  }

}
