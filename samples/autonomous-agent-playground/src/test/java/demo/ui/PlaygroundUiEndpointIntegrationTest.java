package demo.ui;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

public class PlaygroundUiEndpointIntegrationTest extends TestKitSupport {

  @Test
  public void rootRedirectsToPlayground() {
    var response = httpClient.GET("/").invoke();

    assertThat(response.status()).isEqualTo(StatusCodes.FOUND);
    assertThat(response.httpResponse().getHeader("Location")).hasValueSatisfying(
      h -> assertThat(h.value()).isEqualTo("/playground")
    );
  }

  @Test
  public void servesIndexHtmlAtPlaygroundRoot() {
    var response = httpClient.GET("/playground").invoke();

    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    var body = response.body().utf8String();
    assertThat(body).contains("<title>Autonomous Agent Playground</title>");
    assertThat(body).contains("/playground/static/app.js");
  }

  @Test
  public void servesStaticAssets() {
    var akkaCss = httpClient.GET("/playground/static/styles/akka.css").invoke();
    assertThat(akkaCss.status()).isEqualTo(StatusCodes.OK);
    assertThat(akkaCss.body().utf8String()).contains("--primary");

    var appJs = httpClient.GET("/playground/static/app.js").invoke();
    assertThat(appJs.status()).isEqualTo(StatusCodes.OK);
    assertThat(appJs.body().utf8String()).contains("parseRoute");
  }

  @Test
  public void returns404ForMissingAsset() {
    var response = httpClient.GET("/playground/static/does-not-exist.js").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.NOT_FOUND);
  }

  @Test
  public void spaFallbackForSamplePath() {
    var response = httpClient.GET("/playground/research").invoke();

    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body().utf8String()).contains(
      "<title>Autonomous Agent Playground</title>"
    );
  }

  @Test
  public void spaFallbackForRunPath() {
    var response = httpClient.GET("/playground/research/run/abc-123").invoke();

    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.body().utf8String()).contains(
      "<title>Autonomous Agent Playground</title>"
    );
  }
}
