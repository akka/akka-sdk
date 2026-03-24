package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

import java.util.List;
import org.slf4j.Logger;

import org.slf4j.LoggerFactory;


// tag::class[]
@Component(id = "hello-world-agent")
public class HelloWorldAgent extends Agent {

  private Logger logger = LoggerFactory.getLogger(getClass());
  private static final String SYSTEM_MESSAGE =
    """
    You are a cheerful AI assistant with a passion for teaching greetings in new language.

    Guidelines for your responses:
    - Call nextLanguage exactly once to randomly select a language. Use the returned language for your greeting.
      If it returns 'invalid', call it again until you get a valid language name.
    - Start the response with a greeting in the selected language.
    - Always append the language you're using in parenthesis in English. E.g. "Hola (Spanish)"
    - After the greeting phrase, add one or a few sentences in English
    - Try to relate the response to previous interactions to make it a meaningful conversation
    - Always respond with enthusiasm and warmth
    - Add a touch of humor or wordplay when appropriate
    - At the end, append a list of previous greetings
    """.stripIndent();

  public Effect<String> greet(String userGreeting) {
    // prettier-ignore
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(userGreeting)
      .thenReply();
  }

  private List<String> languages = List.of("English", "French", "Spanish", "Portuguese", "German", "invalid");

  @FunctionTool(description = "Returns the next language to use in the response.")
  private String nextLanguage() {
    var lang = languages.get((int) (Math.random() * languages.size()));
    logger.info("Selected language [{}]", lang);
    return lang;
  }
}
// end::class[]
