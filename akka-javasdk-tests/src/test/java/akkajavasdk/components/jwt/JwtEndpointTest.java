/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.jwt;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.http.StrictResponse;
import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class JwtEndpointTest extends TestKitSupport {

    @Test
    public void shouldReturnIssuerAndSubject() {
        CompletableFuture<StrictResponse<String>> call = httpClient.GET("/hello").addHeader("Authorization","Bearer eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleTEifQ.eyJpc3MiOiJteS1pc3N1ZXItMTIzIiwic3ViIjoibXktc3ViamVjdC0xMjMifQ.cDNbT9a-8DJa-Jfu6HrW9mEXfTaXuAUVSF8Sa71LkEA")
                .responseBodyAs(String.class)
                .invokeAsync().toCompletableFuture();

        Awaitility.await()
                .ignoreExceptions()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(call.get().body()).isEqualTo("issuer: my-issuer-123, subject: my-subject-123");
                });

    }

    @Test
    public void shouldReturnForbidden() {
        var call = httpClient.GET("/hello").addHeader("Authorization","Bearer eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleTEifQ.eyJpc3MiOiJteS1pc3N1ZXItMTIzIn0.7d3PJvLIFA22nVW9eNIMJhkR9m5oInTwrRuwtaNNzB8")
                .invokeAsync().toCompletableFuture();

        Awaitility.await()
                .ignoreExceptions()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(call.get().status()).isEqualTo(StatusCodes.FORBIDDEN);
                });

    }

    @Test
    public void shouldRaiseExceptionWhenAccessingJWT() {
        var call = httpClient.GET("/missingjwt").addHeader("Authorization","Bearer eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleTEifQ.eyJpc3MiOiJteS1pc3N1ZXItMTIzIn0.7d3PJvLIFA22nVW9eNIMJhkR9m5oInTwrRuwtaNNzB8")
                .responseBodyAs(String.class).invokeAsync().toCompletableFuture();

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var exception = assertThrows(Exception.class, call::get);
                    assertThat(exception.getCause().getClass()).isEqualTo(RuntimeException.class);
                    assertTrue(exception.getCause().getMessage().contains(
                            "There are no JWT claims defined but trying accessing the JWT claims. The class or the method needs to be annotated with @JWT."));
                });

    }


}
