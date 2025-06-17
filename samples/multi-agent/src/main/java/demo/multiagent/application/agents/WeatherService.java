package demo.multiagent.application.agents;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;

// tag::function-tool[]
public interface WeatherService {

  @FunctionTool(description = "Returns the weather forecast for a given city.") // <1>
  String getWeather(
    @Description("A location or city name.")
    String location, // <2>
    @Description("Forecast for a given date, in yyyy-MM-dd format.")
    String date);
}
// end::function-tool[]
