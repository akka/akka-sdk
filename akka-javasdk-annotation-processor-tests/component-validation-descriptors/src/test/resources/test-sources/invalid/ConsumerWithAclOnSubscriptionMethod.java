package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-acl-method")
@Consume.FromTopic("my-topic")
public class ConsumerWithAclOnSubscriptionMethod extends Consumer {
  // Subscription method has ACL annotation - should fail

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  public Effect onMessage(String message) {
    return effects().done();
  }
}
