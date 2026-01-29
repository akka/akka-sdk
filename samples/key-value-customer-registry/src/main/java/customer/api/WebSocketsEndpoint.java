package customer.api;

import akka.NotUsed;
import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.WebSocket;
import akka.javasdk.client.ComponentClient;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import customer.application.CustomersByCity;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
// tag::ws-view-updates[]
@HttpEndpoint()
public class WebSocketsEndpoint {
  // end::ws-view-updates[]

  private final ComponentClient componentClient;

  public WebSocketsEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // Note: using websocket in a deployed service requires additional steps, see the documentation
  // tag::ws-view-updates[]

  @WebSocket("/websockets/customer-by-city/{cityName}") // <1>
  public Flow<String, String, NotUsed> continousByCityNameWebSocket(String cityName) { // <2>
    // view will keep stream going, toggled with streamUpdates = true on the query
    Source<String, NotUsed> customerSummarySourceJson = componentClient
        .forView()
        .stream(CustomersByCity::continuousCustomersInCity)
        .source(cityName) // <3>
        .map(JsonSupport::encodeToString); // <4>

    return Flow.fromSinkAndSource( // <5>
        // ignore messages from client
        Sink.ignore(),
        // stream view updates
        customerSummarySourceJson
    );
  }

  // end::ws-view-updates[]
}
