package com.example.callanotherservice;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.StrictResponse;
import java.util.List;
import java.util.stream.Collectors;

@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class CallExternalServiceEndpoint {

  private final HttpClient httpClient;

  public record PeopleInSpace(List<Astronaut> people, int number, String message) {}

  public record Astronaut(String craft, String name) {}

  public record AstronautsResponse(List<String> astronautNames) {}

  public CallExternalServiceEndpoint(HttpClientProvider httpClient) { // <1>
    this.httpClient = httpClient.httpClientFor("http://api.open-notify.org"); // <2>
  }

  @Get("/iss-astronauts")
  public AstronautsResponse issAstronauts() {
    StrictResponse<PeopleInSpace> peopleInSpaceResponse = httpClient
      .GET("/astros.json") // <3>
      .responseBodyAs(PeopleInSpace.class) // <4>
      .invoke();

    var astronautNames = peopleInSpaceResponse
      .body()
      .people.stream() // <5>
      .filter(astronaut -> astronaut.craft.equals("ISS"))
      .map(astronaut -> astronaut.name)
      .collect(Collectors.toList());
    return new AstronautsResponse(astronautNames); // <6>
  }
}
