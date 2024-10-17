/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.jwt;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.http.StrictResponse;
import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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


}
