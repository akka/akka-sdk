package com.example.acl;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Acl;
// tag::acl[]
@Acl(denyCode = Acl.DenyStatusCode.NOT_FOUND)
public class UserAction extends Action {
    //...
}
// end::acl[]
