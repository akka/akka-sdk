/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.runtime.sdk.spi.SpiBackofficeServiceSettings
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import kalix.api.projects.v1.projects.ListProjectsRequest
import kalix.api.projects.v1.projects.ListProjectsResponse
import kalix.api.projects.v1.projects.ListRegionsRequest
import kalix.api.projects.v1.projects.ListRegionsResponse
import kalix.api.projects.v1.projects.OrganizationOwner
import kalix.api.projects.v1.projects.Project
import kalix.api.projects.v1.projects.Projects
import kalix.api.projects.v1.projects.ProjectsClient
import kalix.api.projects.v1.projects.ProjectsHandler
import kalix.api.projects.v1.projects.Region
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object BackofficeSettingsLoaderSpec {
  private val ProjectId1 = "11111111-1111-4111-8111-111111111111"
  private val ProjectId2 = "22222222-2222-4222-8222-222222222222"
  private val ProjectId3 = "33333333-3333-4333-8333-333333333333"
  private val OrgId1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
  private val OrgId2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
}

class BackofficeSettingsLoaderSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  import BackofficeSettingsLoaderSpec._

  private val testKit = ActorTestKit(ConfigFactory.parseString("akka.http.server.enable-http2 = on"))
  private val accessToken = "test-access-token"
  private val requestTimeout = 5.seconds

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 20.millis)

  override protected def afterAll(): Unit = testKit.shutdownTestKit()

  private def region(projectId: String, name: String, primary: Boolean = false) =
    Region(
      name = s"projects/$projectId/regions/$name",
      backofficeProxyHostname = s"$name.$projectId.proxy.example.com",
      primary = primary)

  private def org(id: String, friendlyName: String) = OrganizationOwner(s"organizations/$id", friendlyName)

  private def project(
      id: String,
      friendlyName: String,
      owner: Option[OrganizationOwner] = None,
      regions: Seq[Region] = Seq.empty) =
    Project(name = s"projects/$id", friendlyName = friendlyName, owner = owner, regions = regions)

  /** Fake projects service. Records requests and serves paged project and region lists. */
  private class FakeProjectsServer(projects: Seq[Project], regionsByProject: Map[String, Seq[Region]], pageSize: Int) {
    val listProjectsCount = new AtomicInteger()
    val listRegionsRequests = new CopyOnWriteArrayList[String]()
    val authorizationHeaders = new CopyOnWriteArrayList[String]()

    private def page[T](items: Seq[T], pageToken: String): (Seq[T], String) = {
      val offset = if (pageToken.isEmpty) 0 else pageToken.toInt
      val next = offset + pageSize
      (items.slice(offset, next), if (next < items.size) next.toString else "")
    }

    private val service = new Projects {
      override def listProjects(in: ListProjectsRequest): Future[ListProjectsResponse] = {
        listProjectsCount.incrementAndGet()
        val (items, next) = page(projects, in.pageToken)
        Future.successful(ListProjectsResponse(items, next))
      }

      override def listRegions(in: ListRegionsRequest): Future[ListRegionsResponse] = {
        listRegionsRequests.add(in.parent)
        val (items, next) = page(regionsByProject.getOrElse(in.parent, Nil), in.pageToken)
        Future.successful(ListRegionsResponse(items, next))
      }
    }

    private val grpcHandler: HttpRequest => Future[HttpResponse] = ProjectsHandler(service)(testKit.system)
    private val handler: HttpRequest => Future[HttpResponse] = { request =>
      request.headers.find(_.lowercaseName == "authorization").foreach(h => authorizationHeaders.add(h.value))
      grpcHandler(request)
    }

    private val binding = Http()(testKit.system).newServerAt("127.0.0.1", 0).bind(handler).futureValue

    val client: ProjectsClient =
      ProjectsClient(
        GrpcClientSettings
          .connectToServiceAt("127.0.0.1", binding.localAddress.getPort)(testKit.system)
          .withTls(false))(testKit.system)

    def stop(): Unit = {
      client.close().futureValue
      binding.unbind().futureValue
    }
  }

  private def withServer(
      projects: Seq[Project],
      regionsByProject: Map[String, Seq[Region]] = Map.empty,
      pageSize: Int = 100)(test: FakeProjectsServer => Unit): Unit = {
    val server = new FakeProjectsServer(projects, regionsByProject, pageSize)
    try test(server)
    finally server.stop()
  }

  /** Builds a `services` config where each service is a map of its settings. */
  private def servicesConfig(services: (String, Map[String, String])*): Config = {
    val entries = services.map { case (name, settings) =>
      val fields = settings.map { case (k, v) => s"""$k = "$v"""" }.mkString("\n")
      s"""$name { $fields }"""
    }
    ConfigFactory.parseString(entries.mkString("\n"))
  }

  private def resolve(server: FakeProjectsServer, services: (String, Map[String, String])*) =
    BackofficeSettingsLoader.resolveServices(server.client, accessToken, requestTimeout, servicesConfig(services: _*))

  // SpiBackofficeServiceSettings does not define equality, so compare its fields
  private def fields(s: SpiBackofficeServiceSettings) =
    (s.serviceName, s.projectId, s.regionName, s.backofficeProxyHost)

  private def resolveError(server: FakeProjectsServer, services: (String, Map[String, String])*): String =
    the[RuntimeException].thrownBy(resolve(server, services: _*)).getMessage

  "BackofficeSettingsLoader.resolveServices" should {

    "resolve nothing when there are no services" in withServer(Nil) { server =>
      resolve(server) shouldBe empty
      server.listProjectsCount.get() shouldBe 0
    }

    "send the access token as a bearer token" in withServer(
      Seq(project(ProjectId1, "my-project", regions = Seq(region(ProjectId1, "r1", primary = true))))) { server =>
      resolve(server, "svc" -> Map("project" -> "my-project"))
      server.authorizationHeaders.asScala.toSet shouldBe Set(s"Bearer $accessToken")
    }

    "use a project id directly without listing projects, loading its regions" in withServer(
      Nil,
      Map(s"projects/$ProjectId1" -> Seq(region(ProjectId1, "aws-us-east-1", primary = true)))) { server =>
      fields(resolve(server, "svc" -> Map("project" -> ProjectId1))("svc")) shouldBe
      (("svc", ProjectId1, "aws-us-east-1", s"aws-us-east-1.$ProjectId1.proxy.example.com"))
      server.listProjectsCount.get() shouldBe 0
      server.listRegionsRequests.asScala.toList shouldBe List(s"projects/$ProjectId1")
    }

    "resolve a project friendly name using the regions in the project list" in withServer(
      Seq(project(ProjectId1, "my-project", regions = Seq(region(ProjectId1, "gcp-us-east1", primary = true))))) {
      server =>
        fields(resolve(server, "svc" -> Map("project" -> "my-project"))("svc")) shouldBe
        (("svc", ProjectId1, "gcp-us-east1", s"gcp-us-east1.$ProjectId1.proxy.example.com"))
        server.listRegionsRequests shouldBe empty
    }

    "load regions separately when the project list does not include them" in withServer(
      Seq(project(ProjectId1, "my-project")),
      Map(s"projects/$ProjectId1" -> Seq(region(ProjectId1, "gcp-us-east1", primary = true)))) { server =>
      resolve(server, "svc" -> Map("project" -> "my-project")).apply("svc").regionName shouldBe "gcp-us-east1"
      server.listRegionsRequests.asScala.toList shouldBe List(s"projects/$ProjectId1")
    }

    "read all pages of projects and regions" in withServer(
      (1 to 5).map(i => project(s"0000000$i-0000-4000-8000-000000000000", s"project-$i")) :+ project(
        ProjectId1,
        "target"),
      Map(
        s"projects/$ProjectId1" -> Seq(
          region(ProjectId1, "r1"),
          region(ProjectId1, "r2"),
          region(ProjectId1, "r3", primary = true))),
      pageSize = 2) { server =>
      val result = resolve(server, "svc" -> Map("project" -> "target", "region" -> "r3"))
      result("svc").projectId shouldBe ProjectId1
      result("svc").regionName shouldBe "r3"
      server.listProjectsCount.get() shouldBe 3
    }

    "use the service key as service name unless service-name is set" in withServer(
      Seq(project(ProjectId1, "my-project", regions = Seq(region(ProjectId1, "r1", primary = true))))) { server =>
      val result = resolve(
        server,
        "plain" -> Map("project" -> "my-project"),
        "renamed" -> Map("project" -> "my-project", "service-name" -> "actual-name"))
      result("plain").serviceName shouldBe "plain"
      result("renamed").serviceName shouldBe "actual-name"
    }

    "resolve multiple services across projects, listing projects once" in withServer(
      Seq(
        project(ProjectId1, "first", regions = Seq(region(ProjectId1, "r1", primary = true))),
        project(ProjectId2, "second", regions = Seq(region(ProjectId2, "r2", primary = true))))) { server =>
      val result = resolve(server, "a" -> Map("project" -> "first"), "b" -> Map("project" -> "second"))
      result("a").projectId shouldBe ProjectId1
      result("b").projectId shouldBe ProjectId2
      server.listProjectsCount.get() shouldBe 1
    }

    "load the regions of a project once for multiple services" in withServer(
      Nil,
      Map(s"projects/$ProjectId1" -> Seq(region(ProjectId1, "r1", primary = true)))) { server =>
      val result = resolve(server, "a" -> Map("project" -> ProjectId1), "b" -> Map("project" -> ProjectId1))
      result.keySet shouldBe Set("a", "b")
      server.listRegionsRequests.size() shouldBe 1
    }

    "fail when the project friendly name does not exist" in withServer(Seq(project(ProjectId1, "other"))) { server =>
      resolveError(server, "svc" -> Map("project" -> "missing")) should include(
        "Could not find project with friendly name missing")
    }

    "disambiguate projects with the same friendly name by organization friendly name" in withServer(
      Seq(
        project(ProjectId1, "shared", Some(org(OrgId1, "org-one")), Seq(region(ProjectId1, "r1", primary = true))),
        project(ProjectId2, "shared", Some(org(OrgId2, "org-two")), Seq(region(ProjectId2, "r2", primary = true))))) {
      server =>
        resolve(server, "svc" -> Map("project" -> "shared", "organization" -> "org-one"))("svc").projectId shouldBe
        ProjectId1
        resolve(server, "svc" -> Map("project" -> "shared", "organization" -> "org-two"))("svc").projectId shouldBe
        ProjectId2
    }

    "disambiguate projects with the same friendly name by organization id" in withServer(
      Seq(
        project(ProjectId1, "shared", Some(org(OrgId1, "org-one")), Seq(region(ProjectId1, "r1", primary = true))),
        project(ProjectId2, "shared", Some(org(OrgId2, "org-two")), Seq(region(ProjectId2, "r2", primary = true))))) {
      server =>
        resolve(server, "svc" -> Map("project" -> "shared", "organization" -> OrgId2))("svc").projectId shouldBe
        ProjectId2
    }

    "treat a UUID of any version as an organization id" in withServer(
      Seq(
        project(ProjectId1, "shared", Some(org(OrgId1, "org-one")), Seq(region(ProjectId1, "r1", primary = true))),
        project(
          ProjectId2,
          "shared",
          Some(org("01890a5d-ac96-774b-bcce-b302099a8057", "org-v7")),
          Seq(region(ProjectId2, "r2", primary = true))))) { server =>
      resolve(server, "svc" -> Map("project" -> "shared", "organization" -> "01890a5d-ac96-774b-bcce-b302099a8057"))(
        "svc").projectId shouldBe ProjectId2
    }

    "not match an organization id against organization friendly names" in withServer(
      Seq(
        project(ProjectId1, "shared", Some(org(OrgId1, OrgId2)), Seq(region(ProjectId1, "r1", primary = true))),
        project(ProjectId2, "shared", Some(org(OrgId2, "org-two")), Seq(region(ProjectId2, "r2", primary = true))))) {
      server =>
        resolve(server, "svc" -> Map("project" -> "shared", "organization" -> OrgId2))("svc").projectId shouldBe
        ProjectId2
    }

    "fail when a friendly name is ambiguous and no organization is configured" in withServer(
      Seq(
        project(ProjectId1, "shared", Some(org(OrgId1, "org-one"))),
        project(ProjectId2, "shared", Some(org(OrgId2, "org-two"))))) { server =>
      resolveError(server, "svc" -> Map("project" -> "shared")) should include(
        "organization is needed for backoffice service svc")
    }

    "fail when no project with the friendly name is owned by the organization" in withServer(
      Seq(
        project(ProjectId1, "shared", Some(org(OrgId1, "org-one"))),
        project(ProjectId2, "shared", Some(org(OrgId2, "org-two"))))) { server =>
      resolveError(server, "svc" -> Map("project" -> "shared", "organization" -> "org-three")) should include(
        "Could not find project with friendly name shared owned by organization org-three")
      resolveError(server, "svc" -> Map("project" -> "shared", "organization" -> ProjectId3)) should include(
        s"owned by organization $ProjectId3")
    }

    "ignore the organization when the friendly name is unique" in withServer(
      Seq(
        project(ProjectId1, "unique", Some(org(OrgId1, "org-one")), Seq(region(ProjectId1, "r1", primary = true))),
        project(ProjectId2, "shared", Some(org(OrgId1, "org-one"))))) { server =>
      resolve(server, "svc" -> Map("project" -> "unique", "organization" -> "unrelated"))(
        "svc").projectId shouldBe ProjectId1
    }

    "use the single region of a project even when it is not primary" in withServer(
      Seq(project(ProjectId1, "my-project", regions = Seq(region(ProjectId1, "only", primary = false))))) { server =>
      resolve(server, "svc" -> Map("project" -> "my-project"))("svc").regionName shouldBe "only"
    }

    "use the primary region of a multi region project" in withServer(
      Seq(
        project(
          ProjectId1,
          "multi",
          regions = Seq(
            region(ProjectId1, "secondary"),
            region(ProjectId1, "main", primary = true),
            region(ProjectId1, "third"))))) { server =>
      val result = resolve(server, "svc" -> Map("project" -> "multi"))("svc")
      result.regionName shouldBe "main"
      result.backofficeProxyHost shouldBe s"main.$ProjectId1.proxy.example.com"
    }

    "use the configured region of a multi region project" in withServer(
      Seq(
        project(
          ProjectId1,
          "multi",
          regions = Seq(region(ProjectId1, "secondary"), region(ProjectId1, "main", primary = true))))) { server =>
      val result = resolve(server, "svc" -> Map("project" -> "multi", "region" -> "secondary"))("svc")
      fields(result) shouldBe (("svc", ProjectId1, "secondary", s"secondary.$ProjectId1.proxy.example.com"))
    }

    "use the configured region of a project loaded by id" in withServer(
      Nil,
      Map(s"projects/$ProjectId1" -> Seq(region(ProjectId1, "main", primary = true), region(ProjectId1, "other")))) {
      server =>
        resolve(server, "svc" -> Map("project" -> ProjectId1, "region" -> "other"))("svc").regionName shouldBe "other"
    }

    "fail when the configured region does not exist" in withServer(
      Seq(
        project(
          ProjectId1,
          "multi",
          regions = Seq(region(ProjectId1, "main", primary = true), region(ProjectId1, "other"))))) { server =>
      resolveError(server, "svc" -> Map("project" -> "multi", "region" -> "missing")) should include(
        "Region missing not found for project multi")
    }

    "fail when a multi region project has no primary region" in withServer(
      Seq(project(ProjectId1, "multi", regions = Seq(region(ProjectId1, "a"), region(ProjectId1, "b"))))) { server =>
      resolveError(server, "svc" -> Map("project" -> "multi")) should include("has no primary region")
    }

    "fail when the project has no regions" in withServer(Nil, Map.empty) { server =>
      resolveError(server, "svc" -> Map("project" -> ProjectId1)) should include(s"Project $ProjectId1 has no regions")
    }
  }
}
