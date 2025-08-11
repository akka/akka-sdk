package akka.ask.indexer.application;

import static akka.Done.done;
import static java.time.Duration.ofMinutes;

import akka.Done;
import akka.ask.common.OpenAiUtils;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.StepName;
import akka.javasdk.workflow.Workflow;
import com.mongodb.client.MongoClient;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.mongodb.MongoDbEmbeddingStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This workflow reads the files under src/main/resources/md-docs/ and create
 * the vector embeddings that are later
 * used to augment the LLM context.
 */
// tag::shell[]
@ComponentId("rag-indexing-workflow")
public class RagIndexingWorkflow extends Workflow<RagIndexingWorkflow.State> {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final OpenAiEmbeddingModel embeddingModel;
  private final MongoDbEmbeddingStore embeddingStore;
  private final DocumentSplitter splitter;

  // metadata key used to store file name
  private final String srcKey = "src";

  public record State(List<Path> toProcess, List<Path> processed) { // <1>
    public static State of(List<Path> toProcess) {
      return new State(toProcess, new ArrayList<>());
    }

    public Optional<Path> head() { // <2>
      if (toProcess.isEmpty()) return Optional.empty();
      else return Optional.of(toProcess.getFirst());
    }

    public State headProcessed() {
      if (!toProcess.isEmpty()) {
        processed.add(toProcess.removeFirst());
      }
      return new State(toProcess, processed);
    }

    /**
     * @return true if workflow has one or more documents to process, false
     *         otherwise.
     */
    public boolean hasFilesToProcess() {
      return !toProcess.isEmpty();
    }

    public int totalFiles() {
      return processed.size() + toProcess.size();
    }

    public int totalProcessed() {
      return processed.size();
    }
  }

  @Override
  public State emptyState() {
    return State.of(new ArrayList<>());
  }

  // end::shell[]

  // tag::cons[]
  public RagIndexingWorkflow(MongoClient mongoClient) {
    this.embeddingModel = OpenAiUtils.embeddingModel();
    this.embeddingStore = MongoDbEmbeddingStore.builder()
      .fromClient(mongoClient)
      .databaseName("akka-docs")
      .collectionName("embeddings")
      .indexName("default")
      .createIndex(true)
      .build();

    this.splitter = new DocumentByCharacterSplitter(500, 50); // <1>
  }

  // end::cons[]

  // tag::start[]
  public Effect<Done> start() {
    if (currentState().hasFilesToProcess()) {
      return effects().error("Workflow is currently processing documents");
    } else {
      List<Path> documents;
      var documentsDirectoryPath = getClass()
        .getClassLoader()
        .getResource("md-docs")
        .getPath();

      try (Stream<Path> paths = Files.walk(Paths.get(documentsDirectoryPath))) {
        documents = paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".md"))
          .toList();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      return effects()
        .updateState(State.of(documents))
        .transitionTo(RagIndexingWorkflow::processingFileStep) // <1>
        .thenReply(done());
    }
  }

  // end::start[]

  public Effect<Done> abort() {
    logger.debug(
      "Aborting workflow. Current number of pending documents {}",
      currentState().toProcess.size()
    );
    return effects().updateState(emptyState()).pause().thenReply(done());
  }

  // tag::def[]

  @Override
  public WorkflowSettings settings() {
    // prettier-ignore
    return WorkflowSettings.builder()
      .defaultStepTimeout(ofMinutes(1))
      .build();
  }

  @StepName("processing-file")
  private StepEffect processingFileStep() { // <1>
    if (currentState().hasFilesToProcess()) {
      indexFile(currentState().head().get());
    }

    if (currentState().hasFilesToProcess()) { // <2>
      var newState = currentState().headProcessed();
      logger.debug("Processed {}/{}", newState.totalProcessed(), newState.totalFiles());
      return stepEffects()
        .updateState(newState)
        .thenTransitionTo(RagIndexingWorkflow::processingFileStep); // <3>
    } else {
      return stepEffects().thenPause(); // <4>
    }
  }

  // end::def[]

  // tag::index[]
  private void indexFile(Path path) {
    try (InputStream input = Files.newInputStream(path)) {
      // read file as input stream
      Document doc = new TextDocumentParser().parse(input);
      var docWithMetadata = new DefaultDocument(
        doc.text(),
        Metadata.metadata(srcKey, path.getFileName().toString())
      );

      var segments = splitter.split(docWithMetadata);
      logger.debug(
        "Created {} segments for document {}",
        segments.size(),
        path.getFileName()
      );

      segments.forEach(this::addSegment);
    } catch (BlankDocumentException e) {
      // some documents are blank, we need to skip them
    } catch (Exception e) {
      logger.error("Error reading file: {} - {}", path, e.getMessage());
    }
  }

  // end::index[]

  // tag::add[]
  private void addSegment(TextSegment seg) {
    var fileName = seg.metadata().getString(srcKey);
    var res = embeddingModel.embed(seg);

    logger.debug(
      "Segment embedded. Source file '{}'. Tokens usage: in {}, out {}",
      fileName,
      res.tokenUsage().inputTokenCount(),
      res.tokenUsage().outputTokenCount()
    );

    embeddingStore.add(res.content(), seg); // <1>
  }
  // end::add[]
  // tag::shell[]
}
// end::shell[]
