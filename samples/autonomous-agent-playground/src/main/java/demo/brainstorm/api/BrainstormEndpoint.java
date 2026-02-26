package demo.brainstorm.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.brainstorm.application.BrainstormResult;
import demo.brainstorm.application.Curator;
import demo.brainstorm.application.IdeaBoardEntity;
import demo.brainstorm.application.Ideator;
import demo.brainstorm.domain.IdeaBoardState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Emergent brainstorm — many agents contribute to a shared idea board.
 *
 * <p>Demonstrates the emergent coordination pattern: multiple instances of the same simple agent
 * operate on a shared environment entity (the idea board). Agents don't communicate directly — they
 * influence each other only through what they read from and write to the board (stigmergy). A
 * curator agent performs external selection on the collective output.
 *
 * <p>Usage:
 *
 * <pre>
 * # Start a brainstorm with 3 ideators
 * curl -X POST localhost:9000/brainstorm -H "Content-Type: application/json" \
 *   -d '{"topic": "Novel applications of AI in education", "numIdeators": 3}'
 *
 * # Check the idea board
 * curl localhost:9000/brainstorm/{boardId}/board
 *
 * # Check status of all ideator tasks
 * curl localhost:9000/brainstorm/{boardId}
 *
 * # Start curation (after ideators finish)
 * curl -X POST localhost:9000/brainstorm/{boardId}/curate
 *
 * # Check curator result
 * curl localhost:9000/brainstorm/task/{taskId}
 * </pre>
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/brainstorm")
public class BrainstormEndpoint {

  public record CreateBrainstorm(String topic, int numIdeators) {}

  public record BrainstormResponse(String boardId, List<String> ideatorTaskIds) {}

  public record CurateResponse(String curatorTaskId) {}

  public record TaskStatusResponse(
    String status,
    BrainstormResult result,
    String rawResult
  ) {}

  public record BoardStatusResponse(
    String topic,
    int ideaCount,
    List<IdeaBoardState.Idea> ideas,
    List<String> ideatorTaskIds
  ) {}

  private final ComponentClient componentClient;

  public BrainstormEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public BrainstormResponse create(CreateBrainstorm request) {
    var boardId = "board-" + UUID.randomUUID().toString().substring(0, 8);
    var numIdeators = Math.max(2, Math.min(request.numIdeators(), 5));

    // Create the shared environment
    componentClient
      .forEventSourcedEntity(boardId)
      .method(IdeaBoardEntity::create)
      .invoke(new IdeaBoardEntity.CreateRequest(request.topic()));

    // Spawn N ideator agents — each gets the same simple task
    var taskIds = new ArrayList<String>();
    for (int i = 0; i < numIdeators; i++) {
      var taskId = componentClient
        .forAutonomousAgent(Ideator.class)
        .runSingleTask(
          "Brainstorm ideas for '" +
          request.topic() +
          "'. Board ID: " +
          boardId +
          ". Read the board, contribute new ideas, refine and rate existing ones.",
          BrainstormResult.class
        );
      taskIds.add(taskId);
    }

    return new BrainstormResponse(boardId, taskIds);
  }

  @Get("/{boardId}/board")
  public BoardStatusResponse getBoard(String boardId) {
    var state = componentClient
      .forEventSourcedEntity(boardId)
      .method(IdeaBoardEntity::getState)
      .invoke();

    return new BoardStatusResponse(
      state.topic(),
      state.ideas().size(),
      state.ideas(),
      List.of()
    );
  }

  @Post("/{boardId}/curate")
  public CurateResponse curate(String boardId) {
    var taskId = componentClient
      .forAutonomousAgent(Curator.class)
      .runSingleTask(
        "Curate the brainstorm results. Board ID: " +
        boardId +
        ". Read all ideas, select the best, and synthesise into a BrainstormResult.",
        BrainstormResult.class
      );

    return new CurateResponse(taskId);
  }

  @Get("/task/{taskId}")
  public TaskStatusResponse getTask(String taskId) {
    var task = componentClient.forTask(taskId, BrainstormResult.class);
    var state = task.getState();
    return new TaskStatusResponse(state.status().name(), task.getResult(), state.result());
  }
}
