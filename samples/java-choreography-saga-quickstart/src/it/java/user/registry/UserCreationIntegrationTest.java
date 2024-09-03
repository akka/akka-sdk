package user.registry;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import user.registry.api.ApplicationController;
import user.registry.domain.User;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is a skeleton for implementing integration tests for a Kalix application built with the Java SDK.
 * <p>
 * This test will initiate a Kalix Proxy using testcontainers and therefore it's required to have Docker installed
 * on your machine. This test will also start your Spring Boot application.
 * <p>
 * Since this is an integration tests, it interacts with the application using a WebClient
 * (already configured and provided automatically through injection).
 */
public class UserCreationIntegrationTest extends TestKitSupport {

  private final Duration timeout = Duration.ofSeconds(6);

  /**
   * This is a test for a successful user creation.
   * User is correctly created and email is marked as confirmed.
   */

  @Test
  public void testSuccessfulUserCreation() {
    var emailInfoResponse = httpClient.GET("/api/emails/doe@acme.com")
            .responseBodyAs(ApplicationController.EmailInfo.class)
            .invokeAsync();

    assertThat(emailInfoResponse)
            .succeedsWithin(timeout)
            .satisfies(res -> {
              assertThat(res.body().ownerId()).isEmpty();
              assertThat(res.body().status()).isEqualTo("NOT_USED");
            });

    var createUserResponse =
            httpClient.POST("/api/users/001")
                    .withRequestBody(new User.Create("John Doe", "US", "doe@acme.com"))
                    .invokeAsync();

    assertThat(createUserResponse).succeedsWithin(timeout);

    // get email again and check it's eventually confirmed
    Awaitility.await()
            .ignoreExceptions()
            .atMost(timeout)
            .untilAsserted(() -> {
                var emailInfoResponseAfter = httpClient.GET("/api/emails/doe@acme.com")
                        .responseBodyAs(ApplicationController.EmailInfo.class)
                        .invokeAsync();
                assertThat(emailInfoResponseAfter)
                      .succeedsWithin(timeout)
                      .satisfies(res -> {
                        assertThat(res.body().ownerId()).isNotEmpty();
                        assertThat(res.body().status()).isEqualTo("CONFIRMED");
                      });
            });

  }

  /**
   * This is a test for the failure scenario
   * The email is reserved, but we fail to create the user.
   * Timer will fire and cancel the reservation.
   */
  @Test
  public void testUserCreationFailureDueToInvalidInput() throws Exception {
    var emailInfoResponse = httpClient.GET("/api/emails/invalid@acme.com")
            .responseBodyAs(ApplicationController.EmailInfo.class)
            .invokeAsync();
    assertThat(emailInfoResponse)
            .succeedsWithin(timeout)
            .satisfies(res -> {
              assertThat(res.body().ownerId()).isEmpty();
              assertThat(res.body().status()).isEqualTo("NOT_USED");
            });

    var createUserResponse =
            httpClient.POST("/api/users/002")
                    // this user creation will fail because user's name is not provided
                    .withRequestBody(new User.Create(null, "US", "invalid@acme.com"))
                    .invokeAsync();

    assertThat(createUserResponse).succeedsWithin(timeout)
            .satisfies(response -> assertThat(response.httpResponse().status()).isEqualTo(StatusCodes.BAD_REQUEST));

    // email will be reserved for a while, then it will be released
    Awaitility.await()
            .ignoreExceptions()
            .atMost(timeout)
            .untilAsserted(() -> {
              var emailInfoResponseAfter = httpClient.GET("/api/emails/invalid@acme.com")
                      .responseBodyAs(ApplicationController.EmailInfo.class)
                      .invokeAsync();
              assertThat(emailInfoResponseAfter)
                      .succeedsWithin(timeout)
                      .satisfies(res -> {
                        assertThat(res.body().ownerId()).isNotEmpty();
                        assertThat(res.body().status()).isEqualTo("RESERVED");
                      });
            });

    Awaitility.await()
            .ignoreExceptions()
            .timeout(Duration.ofSeconds(10)) //3 seconds for the projection lag + 3 seconds for the timer to fire
            .untilAsserted(() -> {
              var emailInfoResponseAfter = httpClient.GET("/api/emails/invalid@acme.com")
                      .responseBodyAs(ApplicationController.EmailInfo.class)
                      .invokeAsync();
              assertThat(emailInfoResponseAfter)
                      .succeedsWithin(timeout)
                      .satisfies(res -> {
                        assertThat(res.body().ownerId()).isEmpty();
                        assertThat(res.body().status()).isEqualTo("NOT_USED");
                      });
            });

  }

}