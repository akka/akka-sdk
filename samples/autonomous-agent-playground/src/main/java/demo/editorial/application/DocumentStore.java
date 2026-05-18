package demo.editorial.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@Component(id = "editorial-document-store")
public class DocumentStore extends KeyValueEntity<Document> {

  public record SaveRequest(String title, String summary, String content) {}

  public Effect<String> save(SaveRequest request) {
    return effects()
      .updateState(new Document(request.title(), request.summary(), request.content()))
      .thenReply(commandContext().entityId());
  }

  public Effect<Document> get() {
    if (currentState() == null) {
      return effects().error("Document not found");
    }
    return effects().reply(currentState());
  }
}
