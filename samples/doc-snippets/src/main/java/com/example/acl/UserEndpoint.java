package com.example.acl;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;

// tag::endpoint-class[]
@HttpEndpoint("/user")
// end::endpoint-class[]
/*
// tag::class-level-acl[]
@Acl(allow = @Acl.Matcher(service = "service-a"))
// end::class-level-acl[]
*/
/*
// tag::deny-class-level[]
@Acl(allow = @Acl.Matcher(service = "service-a"),
     denyCode = Acl.DenyStatusCode.NOT_FOUND)
// end::deny-class-level[]
 */
// tag::endpoint-class[]
public class UserEndpoint {
  // ...
  // end::endpoint-class[]

  public record CreateUser(String username, String email) { }

  // tag::method-overwrite[]
  @Post
  @Acl(allow = @Acl.Matcher(service = "service-b"))
  public Done createUser(CreateUser create) {
    //... create user logic
    return Done.getInstance();
  }
  // end::method-overwrite[]

  // tag::allow-deny[]
  @Acl(allow = @Acl.Matcher(service = "*"),
       deny = @Acl.Matcher(service = "service-b"))
  // end::allow-deny[]
  public void example1() {
  }

  // tag::all-traffic[]
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
  // end::all-traffic[]
  public void example2() {
  }

  // tag::internet[]
  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  // end::internet[]
  public void example3() {
  }

  // tag::multiple-services[]
  @Acl(allow = {
    @Acl.Matcher(service = "service-a"),
    @Acl.Matcher(service = "service-b")})
  // end::multiple-services[]
  public void example4() {
  }


  // tag::block-traffic[]
  @Acl(allow = {})
  // end::block-traffic[]
  public void example5() {
  }

// tag::endpoint-class[]
}
// end::endpoint-class[]
