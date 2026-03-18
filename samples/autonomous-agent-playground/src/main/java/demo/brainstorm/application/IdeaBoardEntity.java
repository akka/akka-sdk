package demo.brainstorm.application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import demo.brainstorm.domain.IdeaBoardState;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared environment entity for emergent coordination.
 *
 * <p>Agents interact with the idea board indirectly through their tools — reading existing ideas,
 * contributing new ones, refining, and rating. This is the "shared workspace" that enables
 * stigmergy: agents influence each other only through what they leave on the board.
 */
@Component(id = "idea-board")
public class IdeaBoardEntity
  extends EventSourcedEntity<IdeaBoardState, IdeaBoardEntity.BoardEvent> {

  private final String boardId;

  public IdeaBoardEntity(EventSourcedEntityContext context) {
    this.boardId = context.entityId();
  }

  // Events
  public sealed interface BoardEvent {
    record BoardCreated(String topic) implements BoardEvent {}

    record IdeaAdded(String ideaId, String text, String contributor) implements BoardEvent {}

    record IdeaRefined(String ideaId, String refinement) implements BoardEvent {}

    record IdeaRated(String ideaId, int score) implements BoardEvent {}
  }

  @Override
  public IdeaBoardState emptyState() {
    return IdeaBoardState.empty();
  }

  @Override
  public IdeaBoardState applyEvent(BoardEvent event) {
    return switch (event) {
      case BoardEvent.BoardCreated e -> new IdeaBoardState(e.topic(), List.of());
      case BoardEvent.IdeaAdded e -> {
        var ideas = new ArrayList<>(currentState().ideas());
        ideas.add(new IdeaBoardState.Idea(e.ideaId(), e.text(), e.contributor(), 0, null));
        yield new IdeaBoardState(currentState().topic(), List.copyOf(ideas));
      }
      case BoardEvent.IdeaRefined e -> {
        var ideas = currentState()
          .ideas()
          .stream()
          .map(
            idea ->
              idea.id().equals(e.ideaId())
                ? new IdeaBoardState.Idea(
                  idea.id(),
                  idea.text(),
                  idea.contributor(),
                  idea.rating(),
                  e.refinement()
                )
                : idea
          )
          .toList();
        yield new IdeaBoardState(currentState().topic(), ideas);
      }
      case BoardEvent.IdeaRated e -> {
        var ideas = currentState()
          .ideas()
          .stream()
          .map(
            idea ->
              idea.id().equals(e.ideaId())
                ? new IdeaBoardState.Idea(
                  idea.id(),
                  idea.text(),
                  idea.contributor(),
                  idea.rating() + e.score(),
                  idea.refinement()
                )
                : idea
          )
          .toList();
        yield new IdeaBoardState(currentState().topic(), ideas);
      }
    };
  }

  // Commands

  public record CreateRequest(String topic) {}

  public Effect<Done> create(CreateRequest request) {
    if (!currentState().topic().isEmpty()) {
      return effects().reply(done()); // idempotent
    }
    return effects()
      .persist(new BoardEvent.BoardCreated(request.topic()))
      .thenReply(__ -> done());
  }

  public record AddIdeaRequest(String ideaId, String text, String contributor) {}

  public Effect<Done> addIdea(AddIdeaRequest request) {
    return effects()
      .persist(
        new BoardEvent.IdeaAdded(request.ideaId(), request.text(), request.contributor())
      )
      .thenReply(__ -> done());
  }

  public record RefineIdeaRequest(String ideaId, String refinement) {}

  public Effect<Done> refineIdea(RefineIdeaRequest request) {
    return effects()
      .persist(new BoardEvent.IdeaRefined(request.ideaId(), request.refinement()))
      .thenReply(__ -> done());
  }

  public record RateIdeaRequest(String ideaId, int score) {}

  public Effect<Done> rateIdea(RateIdeaRequest request) {
    return effects()
      .persist(new BoardEvent.IdeaRated(request.ideaId(), request.score()))
      .thenReply(__ -> done());
  }

  public ReadOnlyEffect<IdeaBoardState> getState() {
    return effects().reply(currentState());
  }
}
