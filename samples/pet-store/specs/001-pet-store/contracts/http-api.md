# HTTP API Contracts: Pet Store

## PetEndpoint (`/pets`)

### POST /pets
Register a new pet (admin).

**Request**:
```json
{
  "name": "Buddy",
  "species": "Dog",
  "breed": "Golden Retriever",
  "age": 3,
  "photoUrl": "https://example.com/buddy.jpg"
}
```
**Response**: `201 Created`

### GET /pets/{petId}
Get pet details.

**Response** `200 OK`:
```json
{
  "petId": "abc-123",
  "name": "Buddy",
  "species": "Dog",
  "breed": "Golden Retriever",
  "age": 3,
  "photoUrl": "https://example.com/buddy.jpg",
  "status": "Available"
}
```

### PUT /pets/{petId}/make-available
Mark a pet as available (admin).

**Response**: `200 OK`

### PUT /pets/{petId}/remove
Remove a pet from the store (admin).

**Response**: `200 OK`

### GET /pets/available
List all available pets.

**Response** `200 OK`:
```json
{
  "pets": [
    { "petId": "abc-123", "name": "Buddy", "species": "Dog", "breed": "Golden Retriever", "age": 3, "photoUrl": "...", "status": "Available" }
  ]
}
```

### GET /pets/available?species=Dog
List available pets filtered by species.

**Response**: Same structure as above, filtered.

---

## ShoppingCartEndpoint (`/carts`)

### POST /carts/{cartId}/add
Add a pet to the cart.

**Request**:
```json
{ "petId": "abc-123" }
```
**Response**: `200 OK`

### POST /carts/{cartId}/remove
Remove a pet from the cart.

**Request**:
```json
{ "petId": "abc-123" }
```
**Response**: `200 OK`

### GET /carts/{cartId}
Get cart contents.

**Response** `200 OK`:
```json
{
  "cartId": "user-456",
  "petIds": ["abc-123", "def-789"]
}
```

---

## AdoptionEndpoint (`/adoptions`)

### POST /adoptions
Start adoption from a cart.

**Request**:
```json
{
  "cartId": "user-456"
}
```
**Response** `201 Created`:
```json
{
  "adoptionId": "adopt-001"
}
```

### PUT /adoptions/{adoptionId}/confirm
Confirm adoption with adopter info.

**Request**:
```json
{
  "adopterName": "Jane Doe",
  "adopterContact": "jane@example.com"
}
```
**Response**: `200 OK`

### PUT /adoptions/{adoptionId}/cancel
Cancel adoption (releases reserved pets).

**Response**: `200 OK`

### GET /adoptions/{adoptionId}
Get adoption status.

**Response** `200 OK`:
```json
{
  "adoptionId": "adopt-001",
  "petIds": ["abc-123"],
  "adopterName": "Jane Doe",
  "status": "Confirmed"
}
```

---

## PetAssistantEndpoint (`/assistant`)

### POST /assistant/chat
Chat with the AI recommendation agent.

**Request**:
```json
{
  "sessionId": "session-uuid",
  "message": "I have a large yard and two kids, what pet would you recommend?"
}
```
**Response** `200 OK`:
```json
{
  "response": "Based on your large yard and family with kids, I'd recommend..."
}
```