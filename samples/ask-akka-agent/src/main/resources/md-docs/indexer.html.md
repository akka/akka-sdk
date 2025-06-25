<!-- <nav> -->
- [Akka](../../index.html)
- [AI RAG agent part 2: Knowledge indexing with a workflow](indexer.html)

<!-- </nav> -->

# AI RAG agent part 2: Knowledge indexing with a workflow

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

The first step in building a RAG agent is *indexing*. Each time a user submits a query or prompt to the agent, the agent *retrieves* relevant documents by performing a semantic search on a vector database. Before we can perform that search, we need to populate the vector database with all of the knowledge that we want to make available to the agent.

Populating the vector database by creating embeddings is the *indexing* step. In this guide we’re going to use an Akka workflow to manage the indexing of a large number of documents as a long-running process.

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)
You will also need a [Mongo DB Atlas](https://www.mongodb.com/atlas) account. We’ll be using the vector indexing capability of this database for the retrieval portion of the RAG flow. You can do all of the indexing necessary for this sample with a free account. Once you’ve created the account, make note of the secure connection string as you’ll need it later.

If you are following along with each step rather than using the completed solution, then you’ll need the code you wrote in the previous step.

## <a href="about:blank#_unfamiliar_with_concepts_like_vectors_embeddings_or_rag"></a> Unfamiliar with concepts like vectors, embeddings or RAG?

We recommend reviewing our [foundational explainer on AI concepts](../../concepts/ai-concepts-video.html). It offers helpful background that will deepen your understanding of the technologies and patterns used throughout this tutorial.

## <a href="about:blank#_updating_the_pom"></a> Updating the pom

We’re going to use `langchain4j` for this sample, so add those dependencies to your Maven pom file. The full file should look like this when done:

[pom.xml](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/pom.xml)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.akka</groupId>
        <artifactId>akka-javasdk-parent</artifactId>
        <version>3.4.0-M6</version>
    </parent>

    <groupId>akka.ask</groupId>
    <artifactId>ask-akka</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>ask-akka</name>
    <properties>
        <langchain4j.version>1.0.0</langchain4j.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-mongodb-atlas</artifactId>
            <version>1.0.0-beta5</version>
        </dependency>
    </dependencies>
</project>
```

## <a href="about:blank#_adding_a_workflow"></a> Adding a workflow

In your code, add a new empty Java file at `src/main/java/akka/ask/indexer/application/RagIndexingWorkflow.java`. The imports section is large enough that we won’t show it here (you can see it in the source code link).

Let’s start with the outer shell of the workflow class (this won’t compile yet as we haven’t included the workflow definition).

[RagIndexingWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/indexer/application/RagIndexingWorkflow.java)
```java
@ComponentId("rag-indexing-workflow")
public class RagIndexingWorkflow extends Workflow<RagIndexingWorkflow.State> {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final OpenAiEmbeddingModel embeddingModel;
  private final MongoDbEmbeddingStore embeddingStore;
  private final DocumentSplitter splitter;

  // metadata key used to store file name
  private final String srcKey = "src";
  private static final String PROCESSING_FILE_STEP = "processing-file";

  public record State(List<Path> toProcess, List<Path> processed) { // (1)

    public static State of(List<Path> toProcess) {
      return new State(toProcess, new ArrayList<>());
    }

    public Optional<Path> head() { // (2)
      if (toProcess.isEmpty())
        return Optional.empty();
      else
        return Optional.of(toProcess.getFirst());
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
}
```

| **1** | The workflow will maintain a list of files to process and a list of files already processed |
| **2** | We treat the list of files as a queue |
The workflow definition for the document indexer is surprisingly simple:

[RagIndexingWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/indexer/application/RagIndexingWorkflow.java)
```java
@Override
public WorkflowDef<State> definition() {

  var processing = step(PROCESSING_FILE_STEP) // (1)
      .call(() -> {
        if (currentState().hasFilesToProcess()) {
          indexFile(currentState().head().get());
        }
        return done();
      })
      .andThen(Done.class, __ -> {
        // we need to check if it hasFilesToProcess, before moving the head
        // because if workflow is aborted, the state is cleared, and we won't have
        // anything in the list
        if (currentState().hasFilesToProcess()) { // (2)
          var newState = currentState().headProcessed();
          logger.debug("Processed {}/{}", newState.totalProcessed(), newState.totalFiles());
          return effects().updateState(newState).transitionTo(PROCESSING_FILE_STEP); // (3)
        } else {
          return effects().pause(); // (4)
        }
      });

  return workflow()
      .addStep(processing)
      // the processing step is long-running, so we need to set a big timeout
      .defaultStepTimeout(Duration.of(20, MINUTES));
}
```

| **1** | Define the only step in the workflow, `processing` |
| **2** | Check if we have more work to do |
| **3** | If there is more work, transition to `processing` again |
| **4** | If there are no files pending, the workflow will *pause* |
Because this workflow only ever transitions to and from the same state, it might help to think of it as a *recursive* workflow. An interesting aspect of this workflow is that it never ends. If it runs out of files to process, then it simply pauses itself. We haven’t coded it in this sample, but it would be fairly easy to add an endpoint that allowed a user to enqueue more files for the indexer and wake/unpause it.

The actual work of doing the indexing is in the `indexFile` function:

[RagIndexingWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/indexer/application/RagIndexingWorkflow.java)
```java
private void indexFile(Path path) {
  try (InputStream input = Files.newInputStream(path)) {
    // read file as input stream
    Document doc = new TextDocumentParser().parse(input);
    var docWithMetadata = new DefaultDocument(doc.text(), Metadata.metadata(srcKey, path.getFileName().toString()));

    var segments = splitter.split(docWithMetadata);
    logger.debug("Created {} segments for document {}", segments.size(), path.getFileName());

    segments.forEach(this::addSegment);
  } catch (BlankDocumentException e) {
    // some documents are blank, we need to skip them
  } catch (Exception e) {
    logger.error("Error reading file: {} - {}", path, e.getMessage());
  }
}
```
and the `addSegment` function which calls `add` on the embedding store, committing the segment (aka *chunk*) to MongoDB Atlas:

[RagIndexingWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/indexer/application/RagIndexingWorkflow.java)
```java
private void addSegment(TextSegment seg) {
  var fileName = seg.metadata().getString(srcKey);
  var res = embeddingModel.embed(seg);

  logger.debug("Segment embedded. Source file '{}'. Tokens usage: in {}, out {}",
      fileName,
      res.tokenUsage().inputTokenCount(),
      res.tokenUsage().outputTokenCount());

  embeddingStore.add(res.content(), seg); // (1)
}
```

| **1** | Send the embedding segment to the vector database |
Everything that we’ve done so far has been completely asynchronous. When the workflow starts (shown below), it builds the list of pending documents by walking the documents directory and adding each markdown (`*.md`) file it finds. You can find all of these documents in the sample folder `src/main/resources/md-docs`.

[RagIndexingWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/indexer/application/RagIndexingWorkflow.java)
```java
public Effect<Done> start() {
  if (currentState().hasFilesToProcess()) {
    return effects().error("Workflow is currently processing documents");
  } else {
    List<Path> documents;
    var documentsDirectoryPath = getClass().getClassLoader().getResource("md-docs").getPath();

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
        .transitionTo(PROCESSING_FILE_STEP) // (1)
        .thenReply(done());
  }
}
```

| **1** | A workflow must always transition to a state on startup |

## <a href="about:blank#_injecting_the_mongodb_client"></a> Injecting the MongoDB client

If you’ve been following along, then you might be wondering how we inject an `embeddingStore` field into this workflow. This field is of type `MongoDbEmbeddingStore`, and to create an instance of that we need to inject a `MongoClient` to the workflow’s constructor:

[RagIndexingWorkflow.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/indexer/application/RagIndexingWorkflow.java)
```java
public RagIndexingWorkflow(MongoClient mongoClient) {
  this.embeddingModel = OpenAiUtils.embeddingModel();
  this.embeddingStore = MongoDbEmbeddingStore.builder()
      .fromClient(mongoClient)
      .databaseName("akka-docs")
      .collectionName("embeddings")
      .indexName("default")
      .createIndex(true)
      .build();

  this.splitter = new DocumentByCharacterSplitter(500, 50); // (1)
}
```

| **1** | Tweaking the parameters to the document splitter can affect the quality of semantic search results |
The API endpoint to start the indexer creates an instance of the workflow through the standard `ComponentClient` function `forWorkflow`. To make the `MongoClient` instance available, we can use a bootstrap class that uses Akka’s `@Setup` annotation:

[Bootstrap.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/Bootstrap.java)
```java
@Setup
public class Bootstrap implements ServiceSetup {
  public Bootstrap() {
    if (!KeyUtils.hasValidKeys()) {
      throw new IllegalStateException(
          "No API keys found. Make sure you have OPENAI_API_KEY and MONGODB_ATLAS_URI defined as environment variable.");
    }
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    MongoClient mongoClient = MongoClients.create(KeyUtils.readMongoDbUri());


    return new DependencyProvider() {
      @Override
      public <T> T getDependency(Class<T> cls) {
        if (cls.equals(MongoClient.class)) {
          return (T) mongoClient;
        }


        return null;
      }
    };
  }
}
```

## <a href="about:blank#_add_an_endpoint"></a> Add an Endpoint

Add an HTTP endpoint to be able to start the indexing workflow.

[IndexerEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/indexer/api/IndexerEndpoint.java)
```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/api/index")
public class IndexerEndpoint {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ComponentClient componentClient;

  public IndexerEndpoint(ComponentClient componentClient) {

    this.componentClient = componentClient;
  }

  @Post("/start")
  public HttpResponse startIndexation() {
    componentClient.forWorkflow("indexing")
      .method(RagIndexingWorkflow::start)
      .invoke();
    return HttpResponses.accepted();
  }

  @Post("/abort")
  public HttpResponse abortIndexation() {
    componentClient.forWorkflow("indexing")
      .method(RagIndexingWorkflow::abort)
      .invoke();
    return HttpResponses.accepted();
  }
}
```

## <a href="about:blank#_running_the_service"></a> Running the service

As you’ll see in the next step in this guide, we’ll add the indexed knowledge to the agent.

For now, we suggest that you play around with indexing and the kind of results you see in MongoDB. Parameters like the size of chunks can sometimes impact the reliability or quality of the semantic search results. There are also several other types of document splitters. Explore those and see how it impacts the index.

Use the connection URL provided to you by MongoDB Atlas and set the `MONGODB_ATLAS_URI` environment variable to that connection string.

Start your service locally:

```command
mvn compile exec:java
```
In another shell, you can now use `curl` to start the indexing.

```command
curl -XPOST localhost:9000/api/index/start
```
This call will take an extract of the Akka SDK documentation and create a vectorized index in MongoDB.
The documentation files are located in `src/main/resources/md-docs/`. That said, you can also add your own documentation files to this directory.

## <a href="about:blank#_next_steps"></a> Next steps

Next we’ll [add the indexed knowledge to the agent](rag.html) to be able to run meaningful queries against the *Ask Akka* AI assistant!

<!-- <footer> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->