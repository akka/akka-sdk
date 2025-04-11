/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.http.javadsl.model.*;
import akka.javasdk.testkit.TestKitSupport;
import akka.util.ByteString;
import akkajavasdk.components.http.ResourcesEndpoint;
import akkajavasdk.components.http.TestEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStream;
import java.util.OptionalLong;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(Junit5LogCapturing.class)
public class HttpEndpointTest extends TestKitSupport {

  @Test
  public void shouldGetQueryParams() {
    var response = httpClient.GET("/query/one?a=a&b=1&c=-1").responseBodyAs(String.class).invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body()).isEqualTo("name: one, a: a, b: 1, c: -1");
  }

  @Test
  public void shouldServeASingleResource() {
    var response = httpClient.GET("/index.html").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
  }

  @Test
  public void endpointShouldRunOnVirtualThread() {
    var response = httpClient.GET("/on-virtual").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
  }


  @Test
  public void shouldServeWildcardResources() throws Exception {
    var htmlResponse = httpClient.GET("/static/index.html").invoke();
    assertThat(htmlResponse.status()).isEqualTo(StatusCodes.OK);
    assertThat(htmlResponse.httpResponse().entity().getContentType()).isEqualTo(ContentTypes.TEXT_HTML_UTF8);

    try(InputStream in = this.getClass().getClassLoader().getResourceAsStream("static-resources/index.html")) {
      var bytes = ByteString.fromArray(in.readAllBytes());
      assertThat(htmlResponse.body()).isEqualTo(bytes);
      assertThat(htmlResponse.httpResponse().entity().getContentLengthOption()).isEqualTo(
          OptionalLong.of(bytes.size()));
    }

    var otherResourcesWithKnownTypes = Set.of(
        "/static/script.js",
        "/static/style.css",
        "/static/sample-pdf-file.pdf",
        "/static/images/image.png",
        "/static/images/image.jpg",
        "/static/images/image.gif");

    otherResourcesWithKnownTypes.forEach(resourcePath -> {
      var response = httpClient.GET(resourcePath).invoke();
      assertThat(response.httpResponse().entity().getContentType()).isNotEqualTo(ContentTypes.APPLICATION_OCTET_STREAM);
    });

    var response = httpClient.GET("/static/unknown-type.zip").invoke();
    assertThat(response.httpResponse().entity().getContentType()).isEqualTo(ContentTypes.APPLICATION_OCTET_STREAM);
  }

  @Test
  public void return404ForNonexistentResource() {
    var response = httpClient.GET("/static/does-not-exist").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.NOT_FOUND);
  }

  @Test
  public void shouldNotAllowParentPathReferences() {
    // Akka HTTP client normalizes .. so this can't be exploited through a path
    // like this: http://host:port/static/../akkajavasdk/HttpEndpointTest.class
    // a custom user scheme getting the path from somewhere else than request path
    // like this could let the .. through to the classpath resource util though
    var response = httpClient.POST("/static-exploit-try")
        .withRequestBody(new ResourcesEndpoint.SomeRequest("../akkajavasdk/HttpEndpointTest.class")
    ).invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.FORBIDDEN);
  }

  @Test
  public void shouldHandleCollectionsAsBody() {
    var list = List.of(new TestEndpoint.SomeRecord("text", 1));
    var response = httpClient.POST("/list-body")
        .withRequestBody(list)
        .responseBodyAsListOf(TestEndpoint.SomeRecord.class)
        .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body()).isEqualTo(list);
  }


}
