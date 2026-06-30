package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import java.util.List;

// tag::class[]
@Component(id = "chart-agent")
public class ChartAgent extends Agent {

  public static class ChartService {

    @FunctionTool(description = "Renders a chart for the given metric, with a caption")
    public List<MessageContent> renderChart(String metric) { // <1>
      byte[] image = renderChartImage(metric);
      return List.of(
        MessageContent.TextMessageContent.from("Chart for " + metric), // <2>
        MessageContent.ImageMessageContent.fromBytes(image, "image/png")
      ); // <3>
    }

    private byte[] renderChartImage(String metric) {
      // render the chart and return the raw image bytes
      return new byte[0];
    }
  }

  public Effect<String> ask(String question) {
    return effects()
      .systemMessage("You can render charts to help answer questions about metrics.")
      .tools(new ChartService())
      .userMessage(question)
      .thenReply();
  }
}
// end::class[]
