package akka.ask.agent.application;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import dev.langchain4j.service.TokenStream;


public class AkkaStreamUtils {

  /**
   * Converts a TokenStream to an Akka Source.
   * <p>
   * This method will build an Akka Source that is fed with the response produced by TokenStream.
   *
   * The Akka Source is based on a queue. The tokens emitted by TokenStream are passed to the queue and therefore
   * emitted by the Akka Source. The queue is completed when the TokenStream is exhausted.
   */
  public static Source<StreamedResponse, NotUsed> toAkkaSource(TokenStream tokenStream) {
    return
      Source
        .<StreamedResponse>queue(10000)
        .mapMaterializedValue(queue -> {
          // responseTokensCount emitted by tokenStream are passed to the queue
          // that ultimately feeds the Akka Source
          tokenStream
            // the partial responses are the tokens that are streamed out as the response
            .onPartialResponse(msg -> queue.offer(StreamedResponse.partial(msg)))
            // on completion, we receive a ChatResponse that contains the full response text + token usage
            // we emit this last message so we can easily add it to the SessionEntity and store the exchange
            .onCompleteResponse(res -> {
              queue.offer(
                StreamedResponse.lastMessage(
                  res.aiMessage().text(),
                  res.tokenUsage().inputTokenCount(),
                  res.tokenUsage().outputTokenCount()));
              queue.complete();
            })
            .onError(queue::fail)
            .start();

          return NotUsed.getInstance();
        });

  }
}
