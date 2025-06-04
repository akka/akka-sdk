package demo.multiagent.domain;

import akka.javasdk.JsonSupport;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record AgentResponse(String response, String error) {


  private static final Logger logger = LoggerFactory.getLogger(AgentResponse.class);
  public static AgentResponse fromJson(String json) {
    try {
      logger.debug("Parsing JSON: {}", json);
      return JsonSupport.getObjectMapper().readValue(json, AgentResponse.class);
    } catch (JsonProcessingException e) {
      return new AgentResponse("", "Error parsing JSON: " + e.getMessage());
    }
  }

  @JsonIgnore
  public boolean isError() {
    return error != null && !error.isEmpty();
  }

  @JsonIgnore
  public boolean isValid() {
    return response != null && !response.isEmpty();
  }
}
