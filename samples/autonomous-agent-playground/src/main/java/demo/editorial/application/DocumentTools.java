package demo.editorial.application;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import java.util.UUID;

public class DocumentTools {

  private final ComponentClient componentClient;

  public DocumentTools(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(
    description = """
    Save a document to the shared workspace. Returns a document ID that other \
    agents can use to look up the full content. Use this to persist research, drafts, or notes \
    that another agent will need to read.\
    """
  )
  public String saveDocument(
    @Description("Short title for the document") String title,
    @Description("One or two sentence summary") String summary,
    @Description("Full document content") String content
  ) {
    var documentId = UUID.randomUUID().toString();
    componentClient
      .forKeyValueEntity(documentId)
      .method(DocumentStore::save)
      .invoke(new DocumentStore.SaveRequest(title, summary, content));
    return documentId;
  }

  @FunctionTool(
    description = """
    Look up a document by its ID to read the full content. Use this to retrieve \
    research findings, draft sections, or other documents saved by other agents.\
    """
  )
  public Document lookupDocument(
    @Description("The document ID to look up") String documentId
  ) {
    try {
      return componentClient
        .forKeyValueEntity(documentId)
        .method(DocumentStore::get)
        .invoke();
    } catch (Exception e) {
      return new Document("Not found", "Document " + documentId + " was not found", "");
    }
  }
}
