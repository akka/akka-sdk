/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.http.javadsl.model.*;
import akka.http.scaladsl.model.Uri$;
import akka.javasdk.testkit.TestKitSupport;
import akka.util.ByteString;
import akkajavasdk.components.http.ResourcesEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(Junit5LogCapturing.class)
public class HttpEndpointTest extends TestKitSupport {

  @Test
  public void shouldGetQueryParams() {
    var response = await(httpClient.GET("/query/one?a=a&b=1&c=-1").responseBodyAs(String.class).invokeAsync());
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body()).isEqualTo("name: one, a: a, b: 1, c: -1");
  }

  @Test
  public void shouldServeASingleResource() {
    var response = await(httpClient.GET("/index.html").invokeAsync());
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
  }


  @Test
  public void shouldServeWildcardResources() throws Exception {
    var htmlResponse = await(httpClient.GET("/static/index.html").invokeAsync());
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
      var response = await(httpClient.GET(resourcePath).invokeAsync());
      assertThat(response.httpResponse().entity().getContentType()).isNotEqualTo(ContentTypes.APPLICATION_OCTET_STREAM);
    });

    var response = await(httpClient.GET("/static/unknown-type.zip").invokeAsync());
    assertThat(response.httpResponse().entity().getContentType()).isEqualTo(ContentTypes.APPLICATION_OCTET_STREAM);
  }

  @Test
  public void return404ForNonexistentResource() {
    var response = await(httpClient.GET("/static/does-not-exist").invokeAsync());
    assertThat(response.status()).isEqualTo(StatusCodes.NOT_FOUND);
  }

  @Test
  public void shouldNotAllowParentPathReferences() {
    // Akka HTTP client normalizes .. so this can't be exploited through a path
    // like this: http://host:port/static/../akkajavasdk/HttpEndpointTest.class
    // a custom user scheme getting the path from somewhere else than request path
    // like this could let the .. through to the classpath resource util though
    var response = await(httpClient.POST("/static-exploit-try")
        .withRequestBody(new ResourcesEndpoint.SomeRequest("../akkajavasdk/HttpEndpointTest.class")
    ).invokeAsync());
    assertThat(response.status()).isEqualTo(StatusCodes.FORBIDDEN);
  }


}
