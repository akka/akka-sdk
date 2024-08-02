package user.registry;

import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.PlatformServiceSetup;

@PlatformServiceSetup
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class Setup { }