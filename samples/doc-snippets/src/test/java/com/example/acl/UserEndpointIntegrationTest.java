package com.example.acl;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;


// tag::disable-acl-in-it[]
public class UserEndpointIntegrationTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return super.testKitSettings().withAclDisabled();
  }

}
// end::disable-acl-in-it[]
