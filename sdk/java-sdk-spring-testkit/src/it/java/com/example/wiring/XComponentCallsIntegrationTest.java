/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring;

import com.example.Main;
import com.example.wiring.actions.echo.Message;
import com.example.wiring.valueentities.user.User;
import com.example.wiring.valueentities.user.UserEntity;
import kalix.javasdk.client.ComponentClient;
import kalix.spring.KalixConfigurationTest;
import kalix.spring.testkit.AsyncCallsSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static java.time.temporal.ChronoUnit.SECONDS;

@SpringBootTest(classes = Main.class)
@Import(KalixConfigurationTest.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
public class XComponentCallsIntegrationTest extends AsyncCallsSupport {

  @Autowired
  private WebClient webClient;
  @Autowired
  private ComponentClient componentClient;

  private Duration timeout = Duration.of(10, SECONDS);

  @Test
  public void verifyEchoActionXComponentCall() {

    Message response =
      webClient
        .get()
        .uri("/echo/message/{msg}/short", "message to be shortened")
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    Assertions.assertNotNull(response);
    Assertions.assertEquals("Parrot says: 'mssg t b shrtnd'", response.text());
  }

  @Test
  public void verifyEchoActionXComponentCallUsingRequestParam() {

    Message usingGetResponse =
      webClient
        .get()
        .uri(builder -> builder.path("/echo/message-short")
          .queryParam("msg", "message to be shortened")
          .build())
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    Assertions.assertNotNull(usingGetResponse);
    Assertions.assertEquals("Parrot says: 'm3ss4g3 t b3 shrt3n3d'", usingGetResponse.text());

  }

  @Test
  public void verifyEchoActionXComponentCallUsingForward() {

    Message usingGetResponse =
      webClient
        .get()
        .uri("/echo/message/{msg}/leetshort", "message to be shortened")
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    Assertions.assertNotNull(usingGetResponse);
    Assertions.assertEquals("Parrot says: 'm3ss4g3 t b3 shrt3n3d'", usingGetResponse.text());

    Message usingPostResponse =
      webClient
        .post()
        .uri("/echo/message/leetshort")
        .bodyValue(new Message("message to be shortened"))
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    Assertions.assertNotNull(usingPostResponse);
    Assertions.assertEquals("Parrot says: 'm3ss4g3 t b3 shrt3n3d'", usingPostResponse.text());
  }

  @Test
  public void verifyKalixClientUsingPutMethod() {

    User u1 = new User("MayPops", "mary@pops.com");
    Ok userCreation =
      webClient
        .put()
        .uri("/validuser/MaryPops/" + u1.email + "/" + u1.name)
        .retrieve()
        .bodyToMono(Ok.class)
        .block(timeout);
    Assertions.assertEquals(Ok.instance, userCreation);
  }

  @Test
  public void verifyKalixClientUsingPatchMethod() {

    User u1 = new User("MayPops", "mary@pops.com");
    Ok userCreation =
      webClient
        .put()
        .uri("/validuser/MayPatch/" + u1.email + "/" + u1.name)
        .retrieve()
        .bodyToMono(Ok.class)
        .block(timeout);
    Assertions.assertEquals(Ok.instance, userCreation);

    Ok userUpdate =
      webClient
        .patch()
        .uri("/validuser/MayPatch/email/" + "new" + u1.email)
        .retrieve()
        .bodyToMono(Ok.class)
        .block(timeout);
    Assertions.assertEquals(Ok.instance, userUpdate);

    User userGetResponse =
      await(
        componentClient
          .forValueEntity("MayPatch")
          .methodRef(UserEntity::getUser)
          .invokeAsync()
      );
    Assertions.assertEquals("new" + u1.email, userGetResponse.email);
  }

  @Test
  public void verifyKalixClientUsingDeleteMethod() {

    User u1 = new User("mary@delete.com", "MayDelete");
    Ok userCreation =
      webClient
        .put()
        .uri("/validuser/MayDelete/" + u1.email + "/" + u1.name)
        .retrieve()
        .bodyToMono(Ok.class)
        .block(timeout);
    Assertions.assertEquals(Ok.instance, userCreation);

    var userGetResponse =
      await(
        componentClient
          .forValueEntity("MayDelete")
          .methodRef(UserEntity::getUser)
          .invokeAsync()
      );

    Assertions.assertEquals(u1.email, userGetResponse.email);

    Ok userDelete =
      webClient
        .delete()
        .uri("/validuser/MayDelete")
        .retrieve()
        .bodyToMono(Ok.class)
        .block(timeout);
    Assertions.assertEquals(Ok.instance, userDelete);

    var deletedUserException =
      failed(
        componentClient
          .forValueEntity("MayDelete")
          .methodRef(UserEntity::getUser)
          .invokeAsync()
      );

    // FIXME: currently code is sending 500, but it doesn't make sense of an entity to speak http status codes
    // fix endpoints components should return only string messages
    // for the record, this message comes from Spring WebClient
    Assertions.assertTrue(deletedUserException.getMessage().contains("500 Internal Server Error from GET"));
  }
}
