/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Paths
import javax.tools.Diagnostic

import com.example.inventory.InventoryCompiler.CoordinateOptions
import com.example.inventory.InventoryCompiler.DefaultOptions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InventoryProcessorSpec extends AnyWordSpec with Matchers {

  private def expected(name: String): String =
    new String(Files.readAllBytes(Paths.get("src/test/resources/expected").resolve(name)), UTF_8)

  "InventoryProcessor" should {

    "write no inventory without the inventory option" in {
      val result = InventoryCompiler.compile("basic", CoordinateOptions)
      withClue(result.diagnostics) { result.success shouldBe true }
      result.inventory("com.example", "shop") shouldBe None
    }

    "write no inventory when the inventory option is false" in {
      val result = InventoryCompiler.compile("basic", CoordinateOptions :+ "-Aakka.javasdk.inventory=false")
      withClue(result.diagnostics) { result.success shouldBe true }
      result.inventory("com.example", "shop") shouldBe None
    }

    "ignore a broken guardrail without the inventory option" in {
      val result =
        InventoryCompiler.compile("basic", CoordinateOptions, applicationConf = Some("broken-guardrail.conf"))
      withClue(result.diagnostics) { result.success shouldBe true }
      result.inventory("com.example", "shop") shouldBe None
    }

    "emit no warning with -Werror when the inventory option is false" in {
      val result = InventoryCompiler.compile(
        "supported-annotations-only",
        CoordinateOptions ++ Seq("-Aakka.javasdk.inventory=false", "-Xlint:processing", "-Werror"),
        applicationConf = Some("include-guardrails.conf"))
      withClue(result.diagnostics) { result.success shouldBe true }
    }

    "require no coordinates without the inventory option" in {
      val result =
        InventoryCompiler.compile(
          "basic",
          options = Seq.empty,
          processors = "akka.javasdk.tooling.processor.InventoryProcessor")
      withClue(result.diagnostics) { result.success shouldBe true }
    }

    "write the inventory of a service" in {
      val result = InventoryCompiler.compile("basic")
      withClue(result.diagnostics) { result.success shouldBe true }
      new String(result.inventory("com.example", "shop").get, UTF_8) shouldBe expected("basic.json")
    }

    "mark a component library" in {
      val result = InventoryCompiler.compile("basic", DefaultOptions :+ "-Aakka.javasdk.componentLibrary=true")
      new String(result.inventory("com.example", "shop").get, UTF_8) should include("\"componentLibrary\": true,")
    }

    "write the same bytes for the same sources" in {
      val first = InventoryCompiler.compile("basic").inventory("com.example", "shop").get
      val second = InventoryCompiler.compile("basic").inventory("com.example", "shop").get
      second shouldBe first
    }

    "replace an earlier inventory" in {
      val first = InventoryCompiler.compile("basic")
      val second = InventoryCompiler.compile("basic-without-agent", outputDir = Some(first.outputDir))
      val json = new String(second.inventory("com.example", "shop").get, UTF_8)
      json shouldNot include("com.example.shop.SupportAgent")
      json should include("com.example.shop.OrderEntity")
    }

    "write no inventory when validation fails" in {
      val result = InventoryCompiler.compile("invalid-component")
      result.success shouldBe false
      result.inventory("com.example", "shop") shouldBe None
    }

    "write no inventory when the descriptor processor reports an error" in {
      val result = InventoryCompiler.compile("two-setups")
      result.success shouldBe false
      result.diagnostics.map(_.getMessage(null)).mkString should include("More than one class annotated with @Setup")
      result.inventory("com.example", "shop") shouldBe None
    }

    "write an empty guardrails list without application.conf" in {
      val result = InventoryCompiler.compile("basic")
      withClue(result.diagnostics) { result.success shouldBe true }
      val json = new String(result.inventory("com.example", "shop").get, UTF_8)
      json shouldBe expected("basic.json")
      json should endWith("  ],\n  \"guardrails\": []\n}\n")
    }

    "list a guardrail from application.conf" in {
      val result = InventoryCompiler.compile("basic", applicationConf = Some("refund-limit.conf"))
      withClue(result.diagnostics) { result.success shouldBe true }
      new String(result.inventory("com.example", "shop").get, UTF_8) should endWith("""|  ],
           |  "guardrails": [
           |    {
           |      "name": "refund-limit",
           |      "class": "com.example.eval.RefundLimitGuard",
           |      "category": "POLICY",
           |      "reportOnly": false,
           |      "failMode": "fail-closed",
           |      "agents": ["order-agent"],
           |      "agentRoles": [],
           |      "tools": ["issueRefund"],
           |      "controlId": "CTRL-7"
           |    }
           |  ]
           |}
           |""".stripMargin)
    }

    "list an SDK default enabled from application.conf" in {
      val result = InventoryCompiler.compile("basic", applicationConf = Some("default-jailbreak.conf"))
      withClue(result.diagnostics) { result.success shouldBe true }
      new String(result.inventory("com.example", "shop").get, UTF_8) should endWith("""|  "guardrails": [
           |    {
           |      "name": "default model-call jailbreak",
           |      "class": "akka.javasdk.agent.ModelCallSimilarityGuard",
           |      "category": "JAILBREAK",
           |      "reportOnly": false,
           |      "failMode": "fail-closed",
           |      "agents": ["*"],
           |      "agentRoles": [],
           |      "tools": []
           |    }
           |  ]
           |}
           |""".stripMargin)
    }

    "write unresolved guardrail values as unknown and warn for each" in {
      val result = InventoryCompiler.compile("basic", applicationConf = Some("unresolved-guardrail.conf"))
      withClue(result.diagnostics) { result.success shouldBe true }
      new String(result.inventory("com.example", "shop").get, UTF_8) should endWith("""|  "guardrails": [
           |    {
           |      "name": "refund-limit",
           |      "class": "com.example.eval.RefundLimitGuard",
           |      "category": "POLICY",
           |      "reportOnly": "unknown",
           |      "failMode": "fail-closed",
           |      "agents": "unknown",
           |      "agentRoles": [],
           |      "tools": [],
           |      "controlId": "unknown"
           |    },
           |    {
           |      "name": "shared-guard",
           |      "class": "unknown",
           |      "category": "unknown",
           |      "reportOnly": "unknown",
           |      "failMode": "unknown",
           |      "agents": "unknown",
           |      "agentRoles": "unknown",
           |      "tools": "unknown",
           |      "controlId": "unknown"
           |    }
           |  ]
           |}
           |""".stripMargin)

      val warnings = result.diagnostics.filter(_.getKind == Diagnostic.Kind.WARNING).map(_.getMessage(null))
      warnings.count(_.contains("of guardrail [refund-limit] as unknown")) shouldBe 3
      warnings.count(_.contains("of guardrail [shared-guard] as unknown")) shouldBe 8
      warnings.mkString should include("application.conf")
    }

    "warn about an include in application.conf" in {
      val result = InventoryCompiler.compile("basic", applicationConf = Some("include-guardrails.conf"))
      withClue(result.diagnostics) { result.success shouldBe true }
      val warnings = result.diagnostics.filter(_.getKind == Diagnostic.Kind.WARNING).map(_.getMessage(null))
      warnings.mkString should include(
        "The service inventory does not evaluate include \"refund-limit.conf\" in application.conf")
      new String(result.inventory("com.example", "shop").get, UTF_8) should endWith("  \"guardrails\": []\n}\n")
    }

    "write no inventory when a guardrail is invalid" in {
      val result = InventoryCompiler.compile("basic", applicationConf = Some("broken-guardrail.conf"))
      result.success shouldBe false
      val messages = result.diagnostics.map(_.getMessage(null)).mkString
      messages should include("broken")
      messages should include("class")
      result.inventory("com.example", "shop") shouldBe None
    }

    "report an error when the artifact id is missing" in {
      val result = InventoryCompiler.compile(
        "basic",
        options = Seq("-Aakka.javasdk.inventory=true", "-Aakka.javasdk.groupId=com.example"),
        processors = "akka.javasdk.tooling.processor.InventoryProcessor")
      result.success shouldBe false
      result.diagnostics.map(_.getMessage(null)).mkString should include("akka.javasdk.artifactId")
    }

    "claim the annotations" in {
      val result =
        InventoryCompiler.compile("supported-annotations-only", DefaultOptions ++ Seq("-Xlint:processing", "-Werror"))
      withClue(result.diagnostics) { result.success shouldBe true }
      result.inventory("com.example", "shop") shouldBe defined
      new String(result.inventory("com.example", "shop").get, UTF_8) shouldNot include("com.example.shop.ShopSetup")
    }

    "deny every caller of a route without @Acl" in {
      val result = InventoryCompiler.compile("routes-default-deny")
      withClue(result.diagnostics) { result.success shouldBe true }
      new String(result.inventory("com.example", "shop").get, UTF_8) shouldBe expected("routes-default-deny.json")
    }

    "use the method-level @Acl and @JWT in place of the class-level ones" in {
      val result = InventoryCompiler.compile("routes-method-override")
      withClue(result.diagnostics) { result.success shouldBe true }
      new String(result.inventory("com.example", "shop").get, UTF_8) shouldBe expected("routes-method-override.json")
    }

    "write an empty routes array when the compile has no endpoint" in {
      val result = InventoryCompiler.compile("no-endpoint")
      withClue(result.diagnostics) { result.success shouldBe true }
      new String(result.inventory("com.example", "shop").get, UTF_8) should include("  \"routes\": [],\n")
    }

    "deny every caller of a gRPC route without @Acl with PERMISSION_DENIED" in {
      val result = InventoryCompiler.compile("grpc-default-deny")
      withClue(result.diagnostics) { result.success shouldBe true }
      val json = new String(result.inventory("com.example", "shop").get, UTF_8)
      json should include("\"service\": \"com.example.shop.StatusService\",\n      \"method\": \"Status\",")
      json should include("\"acl\": { \"denyAll\": true, \"denyCode\": 7 }")
    }

    "read the method-level @Acl of a gRPC route from the public method that carries it" in {
      val result = InventoryCompiler.compile("grpc-method-overload")
      withClue(result.diagnostics) { result.success shouldBe true }
      val json = new String(result.inventory("com.example", "shop").get, UTF_8)
      json should include(
        "\"method\": \"GetItem\",\n      \"acl\": { \"allow\": [{ \"principal\": \"INTERNET\" }], \"denyCode\": 7 }")
    }

    "report an error for a gRPC endpoint without exactly one interface" in {
      val result = InventoryCompiler.compile("grpc-without-interface")
      result.success shouldBe false
      result.diagnostics.map(_.getMessage(null)).mkString should include(
        "@GrpcEndpoint class [com.example.shop.ShopGrpcEndpoint] must implement exactly one interface, the gRPC service generated by Akka gRPC.")
      result.inventory("com.example", "shop") shouldBe None
    }

    "report an error for a gRPC endpoint whose interface has no service name" in {
      val result = InventoryCompiler.compile("grpc-without-service-name")
      result.success shouldBe false
      result.diagnostics.map(_.getMessage(null)).mkString should include(
        "The gRPC service interface [com.example.shop.ShopService] of @GrpcEndpoint class [com.example.shop.ShopGrpcEndpoint] has no 'name' String constant.")
      result.inventory("com.example", "shop") shouldBe None
    }
  }
}
