package com.example.acl;

import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.KalixService;

@KalixService
// Allow all other Kalix services deployed in the same project to access the components of this
// Kalix service, but disallow access from the internet. This can be overridden explicitly
// per component or method using annotations.
// tag::acl[]
@Acl(allow = @Acl.Matcher(service = "*"))
public class Main {
// end::acl[]
}