package user.registry;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;
import user.registry.application.UniqueEmailEntity;
import user.registry.domain.UniqueEmail;

public class UniqueEmailEntityTest {

  @Test
  public void testReserveAndConfirm() {
    var emailTestKit = KeyValueEntityTestKit.of(UniqueEmailEntity::new);
    reserveEmail(emailTestKit, "joe@acme.com", "1");
    confirmEmail(emailTestKit);
  }

  @Test
  public void testReserveAndUnReserve() {
    var emailTestKit = KeyValueEntityTestKit.of(UniqueEmailEntity::new);
    reserveEmail(emailTestKit, "joe@acme.com", "1");
    unreserveEmail(emailTestKit);

    var state = emailTestKit.method(UniqueEmailEntity::getState).invoke().getReply();
    assertThat(state.isNotInUse()).isTrue();
  }

  @Test
  public void testReserveConfirmAndUnReserve() {
    var emailTestKit = KeyValueEntityTestKit.of(UniqueEmailEntity::new);
    reserveEmail(emailTestKit, "joe@acme.com", "1");
    confirmEmail(emailTestKit);

    // unReserving a confirmed has no effect
    unreserveEmail(emailTestKit);
    var state = emailTestKit.method(UniqueEmailEntity::getState).invoke().getReply();
    assertThat(state.isInUse()).isTrue();
    assertThat(state.isConfirmed()).isTrue();
  }

  @Test
  public void testReserveAndDeleting() {
    var emailTestKit = KeyValueEntityTestKit.of(UniqueEmailEntity::new);
    reserveEmail(emailTestKit, "joe@acme.com", "1");
    markAsNotUsedEmail(emailTestKit);
  }

  @Test
  public void testReserveConfirmAndDeleting() {
    var emailTestKit = KeyValueEntityTestKit.of(UniqueEmailEntity::new);
    reserveEmail(emailTestKit, "joe@acme.com", "1");
    confirmEmail(emailTestKit);
    markAsNotUsedEmail(emailTestKit);
  }

  private static void confirmEmail(
    KeyValueEntityTestKit<UniqueEmail, UniqueEmailEntity> emailTestKit
  ) {
    var confirmedRes = emailTestKit.method(UniqueEmailEntity::confirm).invoke();
    assertThat(confirmedRes.isReply()).isTrue();
    assertThat(confirmedRes.stateWasUpdated()).isTrue();
    var state = emailTestKit.method(UniqueEmailEntity::getState).invoke().getReply();
    assertThat(state.isConfirmed()).isTrue();
  }

  private static void reserveEmail(
    KeyValueEntityTestKit<UniqueEmail, UniqueEmailEntity> emailTestKit,
    String email,
    String ownerId
  ) {
    var reserveCmd = new UniqueEmail.ReserveEmail(email, ownerId);
    var reservedRes = emailTestKit.method(UniqueEmailEntity::reserve).invoke(reserveCmd);
    assertThat(reservedRes.isReply()).isTrue();
    assertThat(reservedRes.stateWasUpdated()).isTrue();

    var state = emailTestKit.method(UniqueEmailEntity::getState).invoke().getReply();
    assertThat(state.isReserved()).isTrue();
  }

  private static void markAsNotUsedEmail(
    KeyValueEntityTestKit<UniqueEmail, UniqueEmailEntity> emailTestKit
  ) {
    var reservedRes = emailTestKit.method(UniqueEmailEntity::markAsNotUsed).invoke();
    assertThat(reservedRes.isReply()).isTrue();
    assertThat(reservedRes.stateWasUpdated()).isTrue();

    var state = emailTestKit.method(UniqueEmailEntity::getState).invoke().getReply();
    assertThat(state.isNotInUse()).isTrue();
  }

  private static void unreserveEmail(
    KeyValueEntityTestKit<UniqueEmail, UniqueEmailEntity> emailTestKit
  ) {
    var reservedRes = emailTestKit.method(UniqueEmailEntity::cancelReservation).invoke();
    assertThat(reservedRes.isReply()).isTrue();
  }
}
