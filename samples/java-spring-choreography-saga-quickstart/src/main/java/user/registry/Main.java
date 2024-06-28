package user.registry;

import kalix.javasdk.annotations.Acl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kalix.javasdk.annotations.KalixService;

@KalixService
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class Main { }