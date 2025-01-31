package com.example.acl;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;

/*
// tag::class-level-acl[]
@Acl(allow = @Acl.Matcher(service = "service-a"))
// end::class-level-acl[]
*/
/*
// tag::deny-class-level[]
@Acl(allow = @Acl.Matcher(service = "service-a"),
     denyCode = 404)
// end::deny-class-level[]
 */
// tag::endpoint-class[]
@HttpEndpoint("/user")
public class UserEndpoint extends AbstractHttpEndpoint { // <1>
  // ...
  // end::endpoint-class[]

  public record CreateUser(String username, String email) { }

  // tag::checking-principals[]
  @Get
  public String checkingPrincipals() {
    // tag::request-context[]
    var principals = requestContext().getPrincipals();
    // end::request-context[]
    if (principals.isInternet()) {
      return "accessed from the Internet";
    } else if (principals.isSelf()) {
      return "accessed from Self (internal call from current service)";
    } else if (principals.isBackoffice()) {
      return "accessed from Backoffice API";
    } else {
      return "accessed from another service: " +
        principals.getLocalService();
    }
  }
  // end::checking-principals[]

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
