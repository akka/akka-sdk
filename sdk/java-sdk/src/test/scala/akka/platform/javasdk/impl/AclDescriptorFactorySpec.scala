/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import scala.reflect.ClassTag

import kalix.FileOptions
import kalix.PrincipalMatcher
import akka.platform.javasdk.annotations.Acl
import akka.platform.spring.testmodels.AclTestModels.MainAllowAllServices
import akka.platform.spring.testmodels.AclTestModels.MainAllowListOfServices
import akka.platform.spring.testmodels.AclTestModels.MainAllowPrincipalAll
import akka.platform.spring.testmodels.AclTestModels.MainAllowPrincipalInternet
import akka.platform.spring.testmodels.AclTestModels.MainDenyAllServices
import akka.platform.spring.testmodels.AclTestModels.MainDenyListOfServices
import akka.platform.spring.testmodels.AclTestModels.MainDenyPrincipalAll
import akka.platform.spring.testmodels.AclTestModels.MainDenyPrincipalInternet
import akka.platform.spring.testmodels.AclTestModels.MainDenyWithCode
import akka.platform.spring.testmodels.AclTestModels.MainWithInvalidAllowAnnotation
import akka.platform.spring.testmodels.AclTestModels.MainWithInvalidDenyAnnotation
import akka.platform.spring.testmodels.AclTestModels.MainWithoutAnnotation
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
