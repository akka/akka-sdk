package user.registry.domain;

import java.util.Optional;

public record UniqueEmail(String address, Status status, Optional<String> ownerId) {

  public enum Status {
    NOT_USED,
    RESERVED,
    CONFIRMED
  }

  public record ReserveEmail(String address, String ownerId) {
  }

  public boolean sameOwner(String ownerId) {
    return this.ownerId.isPresent() && this.ownerId.get().equals(ownerId);
  }

  public boolean notSameOwner(String ownerId) {
    return !sameOwner(ownerId);
  }

  public UniqueEmail asConfirmed() {
    return new UniqueEmail(address, Status.CONFIRMED, ownerId);
  }

  public boolean isConfirmed() {
    return status == Status.CONFIRMED;
  }

  public boolean isInUse() {
    return status != Status.NOT_USED;
  }

  public boolean isNotInUse() {
    return !isInUse();
  }

  public boolean isReserved() {
    return status == Status.RESERVED;
  }
}
