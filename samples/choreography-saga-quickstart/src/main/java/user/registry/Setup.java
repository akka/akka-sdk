package user.registry;

import akka.javasdk.annotations.Acl;

@akka.javasdk.annotations.Setup
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class Setup { }