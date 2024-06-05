package com.example;

import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.KalixService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

@KalixService
// NOTE: This default ACL settings is very permissive as it allows any traffic from the internet.
// Our samples default to this permissive configuration to allow users to easily try it out.
// However, this configuration is not intended to be reproduced in production environments.
// Documentation at https://docs.kalix.io/java/access-control.html
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class Main { }