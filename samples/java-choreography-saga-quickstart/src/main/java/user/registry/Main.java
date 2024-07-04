package user.registry;

import akka.platform.javasdk.annotations.Acl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import akka.platform.javasdk.annotations.KalixService;

@KalixService
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class Main { }