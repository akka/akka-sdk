package akka.ask.agent.application;

public record StreamedResponse(String content, int inputTokens, int outputTokens, boolean finished) {

  public static StreamedResponse partial(String content) {
    return new StreamedResponse(content, 0, 0, false);
  }

  public static StreamedResponse lastMessage(String content, int inputTokens, int outputTokens) {
    return new StreamedResponse(content, inputTokens, outputTokens, true);
  }

  public static StreamedResponse empty() {
    return new StreamedResponse("", 0, 0, true);
  }
}
