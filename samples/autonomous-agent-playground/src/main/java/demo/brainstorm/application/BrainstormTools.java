package demo.brainstorm.application;

import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import java.util.UUID;

/**
 * Tools for the brainstorm sample — uses ComponentClient to interact with the shared
 * IdeaBoardEntity.
 *
 * <p>This demonstrates domain tools with ComponentClient injection, enabling agents to read and
 * write a shared environment entity. Agents interact with each other only through the board.
 */
public class BrainstormTools {

  private final ComponentClient componentClient;

  public BrainstormTools(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @FunctionTool(
    description = "Read the current state of the idea board. Returns all ideas with their ratings and" +
    " refinements."
  )
  public String readBoard(String boardId) {
    try {
      var state = componentClient
        .forEventSourcedEntity(boardId)
        .method(IdeaBoardEntity::getState)
        .invoke();

      if (state.ideas().isEmpty()) {
        return (
          "Idea board for '" + state.topic() + "': No ideas yet. Be the first to contribute!"
        );
      }

      var sb = new StringBuilder();
      sb
        .append("Idea board for '")
        .append(state.topic())
        .append("' (")
        .append(state.ideas().size())
        .append(" ideas):");
      for (var idea : state.ideas()) {
        sb
          .append("\n- [")
          .append(idea.id())
          .append("] (rating: ")
          .append(idea.rating())
          .append(") ");
        sb.append(idea.text());
        if (idea.refinement() != null) {
          sb.append(" | Refined: ").append(idea.refinement());
        }
        sb.append(" (by ").append(idea.contributor()).append(")");
      }
      return sb.toString();
    } catch (Exception e) {
      return "Error reading board: " + e.getMessage();
    }
  }

  @FunctionTool(description = "Contribute a new idea to the board.")
  public String contributeIdea(String boardId, String idea) {
    try {
      var ideaId = UUID.randomUUID().toString().substring(0, 6);
      componentClient
        .forEventSourcedEntity(boardId)
        .method(IdeaBoardEntity::addIdea)
        .invoke(new IdeaBoardEntity.AddIdeaRequest(ideaId, idea, "agent"));

      return "Idea contributed with ID: " + ideaId;
    } catch (Exception e) {
      return "Error contributing idea: " + e.getMessage();
    }
  }

  @FunctionTool(
    description = "Refine an existing idea by adding an improvement or variation."
  )
  public String refineIdea(String boardId, String ideaId, String improvement) {
    try {
      componentClient
        .forEventSourcedEntity(boardId)
        .method(IdeaBoardEntity::refineIdea)
        .invoke(new IdeaBoardEntity.RefineIdeaRequest(ideaId, improvement));

      return "Idea " + ideaId + " refined.";
    } catch (Exception e) {
      return "Error refining idea: " + e.getMessage();
    }
  }

  @FunctionTool(description = "Rate an idea. Score from 1 (weak) to 5 (excellent).")
  public String rateIdea(String boardId, String ideaId, int score) {
    try {
      componentClient
        .forEventSourcedEntity(boardId)
        .method(IdeaBoardEntity::rateIdea)
        .invoke(new IdeaBoardEntity.RateIdeaRequest(ideaId, score));

      return "Idea " + ideaId + " rated with score " + score + ".";
    } catch (Exception e) {
      return "Error rating idea: " + e.getMessage();
    }
  }
}
