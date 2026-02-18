package com.example.application;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import java.util.Optional;

public interface WeatherService {
  @FunctionTool(description = "Returns the weather forecast for a given city.")
  String getWeather(
    @Description("A location or city name.") String location,
    @Description("Forecast for a given date, in yyyy-MM-dd format.") Optional<String> date
  );
}
