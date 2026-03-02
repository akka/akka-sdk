package demo.editorial.application;

import akka.javasdk.annotations.FunctionTool;

/** Tools for the editorial sample. */
public class EditorialTools {

  @FunctionTool(description = "Research a topic to gather facts and background for writing.")
  public String researchTopic(String topic) {
    return (
      "Research findings for '" +
      topic +
      "': " +
      "Key developments in this area include recent policy changes, growing market adoption, " +
      "and three major studies published in the last quarter. Public sentiment is mixed but " +
      "trending positive. Expert consensus favours cautious optimism."
    );
  }

  @FunctionTool(description = "Write a section of content on a given topic and angle.")
  public String writeSection(String topic, String angle) {
    return (
      "Draft section on '" +
      topic +
      "' from angle '" +
      angle +
      "': " +
      "The landscape of " +
      topic +
      " continues to evolve rapidly. " +
      "From the perspective of " +
      angle +
      ", several trends stand out. First, adoption rates have increased 40% year-over-year." +
      " Second, regulatory frameworks are maturing to accommodate innovation. Third, public" +
      " trust metrics show steady improvement as transparency measures take effect. [~300 words" +
      " of detailed analysis would follow]"
    );
  }
}
