package user.registry.application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.domain.UniqueEmail;

/**
 * Entity wrapping a UniqueEmail.
 * <p>
 * The UniqueEmailEntity is part of the application layer. It implements the glue between the domain layer (UniqueEmail)
 * and Akka. Incoming commands are delivered to the UniqueEmailEntity, which passes them to the domain layer.
 * The domain layer mutates and the new state is passed back to the entity. The entity wraps it in an {@link Effect} that
 * describes to Akka what needs to be done, e.g. update the state, reply to the caller, etc.
 * <p>
 * This entity works as a barrier to ensure that an email address is only used once.
 * In the process of creating a user, the email address is reserved.
 * If the user creation fails, the email reservation is cancelled. Otherwise, it is confirmed.
 * <p>
 * If, while creating a user, the email address is already reserved, the user creation fails.
 */
@ComponentId("unique-address")
public class UniqueEmailEntity extends KeyValueEntity<UniqueEmail> {

  private final String address;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
  @JsonSubTypes(
    {
      @JsonSubTypes.Type(value = Result.Success.class, name = "Success"),
      @JsonSubTypes.Type(value = Result.AlreadyReserved.class, name = "AlreadyReserved"),
    }
  )
  public sealed interface Result {
    record AlreadyReserved() implements Result {}

    record Success() implements Result {}
  }

  public UniqueEmailEntity(KeyValueEntityContext context) {
    this.address = context.entityId();
  }

  /**
   * This is the initial state of the entity.
   * When the entity is created, it is not used.
   * It can be reverted to this state by calling the delete() method.
   */
  private UniqueEmail notInUse() {
    return new UniqueEmail(address, UniqueEmail.Status.NOT_USED, Optional.empty());
  }

  /**
   * For the initial state, we return an email address that is not in use.
   */
  @Override
  public UniqueEmail emptyState() {
    return notInUse();
  }

  /**
   * This method reserves an email address.
   * If the email address is already in use (reserved or confirmed) and we are trying to reserve it for a different user,
   * the call will fail.
   * <p>
   * If we are trying to reserve the email address for the same user, the call will succeed, but won't change the email state.
   * <p>
   * If the email address is not in use at all, the call will succeed and the email address will be reserved for the given user.
   */
  public Effect<Result> reserve(UniqueEmail.ReserveEmail cmd) {
    if (currentState().isInUse() && currentState().notSameOwner(cmd.ownerId())) {
      return effects().reply(new Result.AlreadyReserved());
    }

    if (currentState().sameOwner(cmd.ownerId())) {
      return effects().reply(new Result.Success());
    }

    logger.info("Reserving email address '{}'", cmd.address());
    return effects()
      .updateState(
        new UniqueEmail(
          cmd.address(),
          UniqueEmail.Status.RESERVED,
          Optional.of(cmd.ownerId())
        )
      )
      .thenReply(new Result.Success());
  }

  /**
   * This method is called when the email address is confirmed.
   * This happens when UserEventsSubscriber sees a UserWasCreated event or an EmailAssigned event.
   */
  public Effect<Done> confirm() {
    if (currentState().isReserved()) {
      logger.info("Confirming email address '{}'", currentState().address());
      return effects().updateState(currentState().asConfirmed()).thenReply(done());
    } else {
      logger.info("Email address status is not reserved. Ignoring confirmation request.");
      return effects().reply(done());
    }
  }

  /**
   * This method is called when the email address is no longer used.
   * It's only called from the scheduled timer (see UniqueEmailSubscriber).
   * <p>
   * When the timer fires, it cancels the reservation but only if it is not confirmed.
   * If it's already confirm or if in the meantime the email is not in use anymore, the call has no effect.
   */
  public Effect<Done> cancelReservation() {
    if (currentState().isReserved()) {
      logger.info("Cancelling email address reservation'{}'", currentState().address());
      // when cancelling, we go back to the initial state (not in use)
      return effects().updateState(notInUse()).thenReply(done());
    } else {
      return effects().reply(done());
    }
  }

  /**
   * This method is called when the email address is no longer used.
   * this method is called from the UserEventsSubscriber when a user stops using an email address.
   * <p>
   * It doesn't verify if the email address is reserved or confirmed.
   * Whatever the status is, it will move back to 'not-in-use'.
   * Strictly speaking, since this method is only called from UserEventsSubscriber, it will never be called when the email
   * is still in RESERVED state. The ordering of events is guaranteed, so first the UserWasCreated event will be processed
   * confirming the email, then later an EmailUnassigned event might be emitted and this method will be called.
   */
  public Effect<Done> markAsNotUsed() {
    logger.info("Marking as not used email address '{}'", currentState().address());
    return effects().updateState(notInUse()).thenReply(done());
  }

  public ReadOnlyEffect<UniqueEmail> getState() {
    return effects().reply(currentState());
  }
}
