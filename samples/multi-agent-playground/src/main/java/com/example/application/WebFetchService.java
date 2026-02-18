package com.example.application;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import java.net.URI;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebFetchService {

  private static final Logger logger = LoggerFactory.getLogger(WebFetchService.class);
  private final HttpClientProvider httpClientProvider;

  public WebFetchService(HttpClientProvider httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  @FunctionTool(description = "Fetch and read the text content of a specific webpage URL.")
  public String fetchContent(@Description("The full URL of the webpage to read") String url) {
    try {
      URI uri = new URI(url);
      String baseUrl = uri.getScheme() + "://" + uri.getHost();
      String path = uri.getRawPath();
      if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();

      logger.info("Fetching content from: {}", url);

      HttpClient client = httpClientProvider.httpClientFor(baseUrl);
      var response = client.GET(path).invoke();

      if (response.httpResponse().status().isSuccess()) {
        String html = response.body().utf8String();
        // Use Jsoup to extract text and remove noise (scripts, styles, etc.)
        String cleanText = Jsoup.parse(html).text();

        // Truncate to avoid hitting LLM token limits (e.g., ~10k characters)
        if (cleanText.length() > 10000) {
          cleanText = cleanText.substring(0, 10000) + "... [Truncated]";
        }

        return cleanText;
      } else {
        return "Failed to fetch content. Status: " + response.httpResponse().status();
      }
    } catch (Exception e) {
      logger.error("Error fetching URL: " + url, e);
      return "Error fetching content: " + e.getMessage();
    }
  }
}
