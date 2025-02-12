/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(Junit5LogCapturing.class)
public class HttpEndpointTest extends TestKitSupport {

  @Test
  public void shouldServeASingleResource() {
    var response = await(httpClient.GET("/index.html").invokeAsync());
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
  }


  @Test
  public void shouldServeWildcardResource() {
    var response = await(httpClient.GET("/static/index.html").invokeAsync());
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
  }


}
