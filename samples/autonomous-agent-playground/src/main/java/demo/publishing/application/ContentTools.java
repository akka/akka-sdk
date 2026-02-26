package demo.publishing.application;

import akka.javasdk.annotations.FunctionTool;

/** Tools for content research and writing. */
public class ContentTools {

  @FunctionTool(
    description = "Research a topic and return background information and key facts"
  )
  public String researchTopic(String topic) {
    return (
      "Research results for '" +
      topic +
      "': " +
      "Key trends include increased adoption across industries, " +
      "recent breakthroughs in efficiency and scalability, " +
      "and growing investment from major technology companies. " +
      "Experts predict significant impact on productivity within 2-3 years."
    );
  }

  @FunctionTool(
    description = "Write an article draft given a topic and research material. Returns the draft text."
  )
  public String writeDraft(String topic, String research) {
    return (
      "Draft article on '" +
      topic +
      "':\n\n" +
      "# " +
      topic +
      "\n\n" +
      "The landscape of " +
      topic +
      " is rapidly evolving. " +
      research +
      "\n\n" +
      "In conclusion, " +
      topic +
      " represents a transformative opportunity " +
      "that organizations should evaluate carefully."
    );
  }
}
