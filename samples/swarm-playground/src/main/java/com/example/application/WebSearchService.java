package com.example.application;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.http.HttpClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class WebSearchService {

  private static final Logger logger = LoggerFactory.getLogger(WebSearchService.class);

  private final HttpClient httpClient;
  private final String apiKey;
  private final String cx;

  public WebSearchService(HttpClient httpClient, String apiKey, String cx) {
    this.httpClient = httpClient;
    this.apiKey = apiKey;
    this.cx = cx;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GoogleResponse(List<Item> items) {}
  
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Item(String title, String snippet, String link) {}

  @FunctionTool(description = "Search the web for up-to-date information, news, and facts about a specific topic.")
  public String search(@Description("The search query or topic to look up") String query) {
    if (apiKey == null || apiKey.isEmpty() || cx == null || cx.isEmpty()) {
        logger.warn("Google Search API credentials not configured. Returning simulated results.");
        return simulateSearch(query);
    }

    try {
      String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
      String path = String.format("/customsearch/v1?key=%s&cx=%s&q=%s", apiKey, cx, encodedQuery);
      
      var response = httpClient.GET(path).invoke();
      
      if (response.httpResponse().status().isSuccess()) {
        // Use Jackson ObjectMapper from JsonSupport to decode the response body
        GoogleResponse data = JsonSupport.getObjectMapper().readValue(response.body().utf8String(), GoogleResponse.class);
        
        if (data.items() == null || data.items().isEmpty()) {
            return "No results found for: " + query;
        }
        
        return data.items().stream()
            .limit(5)
            .map(item -> String.format("- %s: %s (%s)", item.title(), item.snippet(), item.link()))
            .collect(Collectors.joining("\n"));
      } else {
        logger.error("Google Search API call failed with status: {}. Body: {}", 
            response.httpResponse().status(), response.body().utf8String());
        return "Error performing search. API returned: " + response.httpResponse().status();
      }
    } catch (Exception e) {
      logger.error("Exception during web search", e);
      return "Error performing search: " + e.getMessage();
    }
  }

  private String simulateSearch(String query) {
    return "[SIMULATED] Search results for '" + query + "':\n- Fact: This is a simulated result because API keys are missing.";
  }
}
