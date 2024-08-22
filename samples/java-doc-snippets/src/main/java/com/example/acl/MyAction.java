package com.example.acl;

import akka.Done;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;
import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.Consume;

// FIXME should it rather be an endpoint?
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class MyAction extends Action {

    // tag::allow-deny[]
    @Acl(allow = @Acl.Matcher(service = "*"),
            deny = @Acl.Matcher(service = "service-b"))
    public Effect<String> createUser(CreateUser create) {
        //...
        // end::allow-deny[]
        return null;
        // tag::allow-deny[]
    }
    // end::allow-deny[]

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
    public void example4() {}

    // tag::block-traffic[]
    @Acl(allow = {})
    // end::block-traffic[]
    public void example5() {
    }

    // tag::deny-code[]
    // end::deny-code[]
    @Acl(allow = @Acl.Matcher(service = "*"), denyCode = Acl.DenyStatusCode.NOT_FOUND)
    public Effect<String> updateUser(CreateUser create) {
        //...
        // end::deny-code[]
        return null;
        // tag::deny-code[]
    }

    // tag::open-subscription-acl[]
    @Acl(allow = @Acl.Matcher(service = "*"))
    public Effect<Done> changes(CounterState counterState) {
     //...
        // end::open-subscription-acl[]
        return null;
        // tag::open-subscription-acl[]
    }
    // end::open-subscription-acl[]


}

class CreateUser{}
class Counter extends KeyValueEntity<Integer> {}
class CounterState{}