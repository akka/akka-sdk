

<-nav->

- [  Akka](../../index.html)
- [  Developing](../index.html)
- [  Samples](../samples.html)
- [  AI RAG Agent](index.html)
- [  Knowledge indexing with a workflow](indexer.html)



</-nav->



# Knowledge indexing with a workflow

## [](about:blank#_overview) Overview

The first step in building a RAG agent is *indexing* . Each time a user submits a query or prompt to the agent, the agent *retrieves* relevant documents by performing a semantic search on a vector database. Before we can perform that search, we need to populate the vector database with all of the knowledge that we want to make available to the agent.

Populating the vector database by creating embeddings is the *indexing* step. In this guide we’re going to use an Akka workflow to manage the indexing of a large number of documents as a long-running process.

## [](about:blank#_prerequisites) Prerequisites

- Java 21, we recommend[  Eclipse Adoptium](https://adoptium.net/marketplace/)
- [  Apache Maven](https://maven.apache.org/install.html)   version 3.9 or later
- <a href="https://curl.se/download.html"> `curl`   command-line tool</a>
- An[  Akka account](https://console.akka.io/register)
- [  Docker Engine](https://docs.docker.com/get-started/get-docker/)   27 or later

You will also need a [Mongo DB Atlas](https://www.mongodb.com/atlas) account. We’ll be using the vector indexing capability of this database for the retrieval portion of the RAG flow. You can do all of the indexing necessary for this sample with a free account. Once you’ve created the account, make note of the secure connection string as you’ll need it later.

If you are following along with each step rather than using the completed solution, then you’ll need the code you wrote in the previous step.

## [](about:blank#_updating_the_pom) Updating the pom

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
        <version>3.3.0</version>
    </parent>

    <groupId>akka.ask</groupId>
    <artifactId>ask-akka</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>ask-akka</name>
    <properties>
        <langchain4j.version>1.0.0-beta1</langchain4j.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-mongodb-atlas</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
    </dependencies>
</project>
```

## [](about:blank#_adding_a_workflow) Adding a workflow

In your code, add a new empty Java file at `src/main/java/akka/ask/indexer/application/RagIndexingWorkflow.java` . The imports section is large enough that we won’t show it here (you can see it in the source code link).

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

| **  1** | The workflow will maintain a list of files to process and a list of files already processed |
| **  2** | We treat the list of files as a queue |

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

  return workflow().addStep(processing);
}
```

| **  1** | Define the only step in the workflow, `processing` |
| **  2** | Check if we have more work to do |
| **  3** | If there is more work, transition to `processing`   again |
| **  4** | If there are no files pending, the workflow will*  pause* |

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

and the `addSegment` function which calls `add` on the embedding store, committing the segment (aka *chunk* ) to MongoDB Atlas:

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

| **  1** | Send the embedding segment to the vector database |

Everything that we’ve done so far has been completely asynchronous. When the workflow starts (shown below), it builds the list of pending documents by walking the documents directory and adding each markdown ( `*.md` ) file it finds. You can find all of these documents in the sample folder `src/main/resources/flat-doc`.

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

| **  1** | A workflow must always transition to a state on startup |

## [](about:blank#_injecting_the_mongodb_client) Injecting the MongoDB client

If you’ve been following along, then you might be wondering how we inject an `embeddingStore` field into this workflow. This field is of type `MongoDbEmbeddingStore` , and to create an instance of that we need to inject a `MongoClient` to the workflow’s constructor:

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

  this.splitter = new DocumentByCharacterSplitter(500, 50, OpenAiUtils.buildTokenizer()); // (1)
}
```

| **  1** | Tweaking the parameters to the document splitter can affect the quality of semantic search results |

The API endpoint to start the indexer creates an instance of the workflow through the standard `ComponentClient` function `forWorkflow` . To make the `MongoClient` instance available, we can use a bootstrap class that uses Akka’s `@Setup` attribute:

[Bootstrap.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/Bootstrap.java)
```java
@Setup
public class Bootstrap implements ServiceSetup {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final MongoClient mongoClient;
  private final ComponentClient componentClient;

  public Bootstrap(
      ComponentClient componentClient,
      Materializer materializer) {

    if (!KeyUtils.hasValidKeys()) {
      throw new IllegalStateException(
          "No API keys found. When running locally, make sure you have a " + ".env.local file located under " +
              "src/main/resources/ (see src/main/resources/.env.example). When running in production, " +
              "make sure you have OPENAI_API_KEY and MONGODB_ATLAS_URI defined as environment variable.");
    }

    this.componentClient = componentClient;
    this.mongoClient = MongoClients.create(KeyUtils.readMongoDbUri());
  }

  @Override
  public DependencyProvider createDependencyProvider() {
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

As you’ll see in the next step in this guide, we’ll add to this bootstrap to inject a service that does the actual LLM communication for us.

For now, we suggest that you play around with indexing and the kind of results you see in MongoDB. Parameters like the size of chunks can sometimes impact the reliability or quality of the semantic search results. There are also several other types of document splitters. Explore those and see how it impacts the index.

You can set the `OPENAI_API_KEY` environment variable something random as you don’t need it yet. Use the connection URL provided to you by MongoDB Atlas and set the `MONGODB_ATLAS_URI` environment variable to that connection string.

## [](about:blank#_next_steps) Next steps

Next we’ll write a service (in the dependency injection sense, not the Akka sense) that does all of the asynchronous LLM communication work. We’ll then put an API in front of it and be able to run queries against the *Ask Akka* AI assistant!



<-footer->


<-nav->
[Modeling the agent session](session.html) [Executing RAG queries](rag.html)

</-nav->


</-footer->


<-aside->


</-aside->
