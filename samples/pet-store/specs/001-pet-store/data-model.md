# Data Model: Pet Store

**Branch**: `001-pet-store` | **Date**: 2026-03-01

## Entities

### Pet (Event Sourced Entity)

**State record**: `Pet`

| Field    | Type   | Description                        |
|----------|--------|------------------------------------|
| petId    | String | Unique identifier                  |
| name     | String | Pet's name                         |
| species  | String | Species (Dog, Cat, Bird, etc.)     |
| breed    | String | Breed within species               |
| age      | int    | Age in years                       |
| photoUrl | String | URL to pet photo                   |
| status   | Status | Current lifecycle status           |

**Status enum**: `Registered`, `Available`, `Reserved`, `Adopted`, `Removed`

**Valid state transitions**:

```
Registered → Available
Registered → Removed
Available  → Reserved
Available  → Removed
Reserved   → Adopted
Reserved   → Available  (adoption cancelled)
```

Terminal states: `Adopted`, `Removed` (no transitions out)

**Events** (sealed interface `PetEvent`):

| Event         | TypeName          | Fields                                          |
|---------------|-------------------|-------------------------------------------------|
| Registered    | pet-registered    | name, species, breed, age, photoUrl             |
| MadeAvailable | pet-made-available| (none — status change only)                     |
| Reserved      | pet-reserved      | adoptionId                                      |
| Adopted       | pet-adopted       | adoptionId                                      |
| Released      | pet-released      | (none — back to Available from Reserved)        |
| Removed       | pet-removed       | (none — removed from store)                     |

**Validation rules**:
- Name, species, breed MUST be non-empty
- Age MUST be >= 0
- Status transitions enforced by domain logic (reject invalid transitions)

---

### ShoppingCart (Key Value Entity)

**State record**: `ShoppingCart`

| Field   | Type         | Description                    |
|---------|--------------|--------------------------------|
| cartId  | String       | User/session identifier        |
| petIds  | List<String> | Pet IDs in the cart            |

**Commands**:
- `addPet(petId)` — add pet to cart (reject duplicates)
- `removePet(petId)` — remove pet from cart
- `getCart()` — return current cart state
- `checkout()` — return pet IDs and clear cart

**Validation rules**:
- Cannot add the same pet ID twice
- Cannot remove a pet ID not in the cart

---

### Adoption (Workflow)

**State record**: `Adoption`

| Field         | Type         | Description                    |
|---------------|--------------|--------------------------------|
| adoptionId    | String       | Unique identifier (workflow ID)|
| cartId        | String       | Cart/user that initiated       |
| petIds        | List<String> | Pets being adopted             |
| adopterName   | String       | Adopter's name                 |
| adopterContact| String       | Adopter's contact info         |
| status        | Status       | Workflow status                |

**Workflow status enum**: `Started`, `PetsReserved`, `Confirmed`, `Cancelled`, `Failed`

**Workflow steps**:

1. `reservePetsStep` — Call PetEntity.reserve() for each pet. On failure → compensate.
2. `waitForConfirmation` — Pause workflow. Wait for user to confirm with adopter info or cancel.
3. `confirmAdoptionStep` — Call PetEntity.adopt() for each pet. Transition to end.
4. `cancelAdoptionStep` (compensation) — Call PetEntity.release() for each reserved pet. Transition to end.

**Timeouts**:
- Reservation hold: configurable (e.g., 15 minutes) before auto-cancel
- Step timeout: 10 seconds for entity calls

---

## Views

### AvailablePetsBySpeciesView

**Row record**: `PetEntry`

| Field    | Type    | Description              |
|----------|---------|--------------------------|
| petId    | String  | Pet identifier           |
| name     | String  | Pet name                 |
| species  | String  | Species                  |
| breed    | String  | Breed                    |
| age      | int     | Age in years             |
| photoUrl | String  | Photo URL                |
| status   | String  | Current status           |

**Consumes from**: `PetEntity` (Event Sourced — uses `onEvent`)

**Queries**:
- `SELECT * AS pets FROM available_pets WHERE species = :species AND status = 'Available'`
- `SELECT * AS pets FROM available_pets WHERE status = 'Available'`

---

## Agents

### PetRecommendationAgent

- **Input**: User's preferences (free text)
- **Output**: Structured recommendation (list of pet suggestions with reasoning)
- **Tools**:
  - `@FunctionTool getAvailablePets(species)` — queries the view for available pets
- **Session**: UUID-based for new conversations
