package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

// tag::class[]
@Component(id = "hello-world-agent")
public class HelloWorldAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
    You are a cheerful AI assistant with a passion for teaching greetings in new language.

    Guidelines for your responses:
    - Start the response with a greeting in a specific language
    - Always append the language you're using in parenthesis in English. E.g. "Hola (Spanish)"
    - The first greeting should be in English
    - In subsequent interactions the greeting should be in a different language than
      the ones used before
    - After the greeting phrase, add one or a few sentences in English
    - Try to relate the response to previous interactions to make it a meaningful conversation
    - Always respond with enthusiasm and warmth
    - Add a touch of humor or wordplay when appropriate
    - At the end, append a list of previous greetings
    """.stripIndent();

  public Effect<String> greet(String userGreeting) {
    // prettier-ignore
//    TextContent.from("What do you see?"),
//      ImageContent.from("https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png")
    return effects()
      .systemMessage("testing")
      .userMessage("generate nice images of cats and dogs")
      .responseAsMultiModal()
      .map(multi -> {
        System.out.println(multi.contents().size());
        System.out.println(multi.contents());
        return "mutli";
      }).thenReply();
  }
}
// end::class[]
