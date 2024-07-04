package com.example.acl;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Acl;

// tag::acl1[]
// tag::acl[]
@Acl(allow = @Acl.Matcher(service = "service-a"))
public class EmployeeAction extends Action {
    //...
// end::acl[]


    @Acl(allow = @Acl.Matcher(service = "service-b"))
    public Effect<String> createEmployee(CreateEmployee create) {
        //...
// end::acl1[]
        return null;
// tag::acl1[]
    }
// tag::acl[]
}
// end::acl[]
// end::acl1[]
class CreateEmployee{}