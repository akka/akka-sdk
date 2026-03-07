# Quickstart: Pet Store

## Prerequisites

- Java 21+
- Maven 3.9+
- OpenAI API key (for AI assistant feature)

## Build & Run

```shell
mvn compile exec:java
```

The service starts on `http://localhost:9000`.

## Try It Out

### 1. Register a Pet

```shell
curl -X POST http://localhost:9000/pets \
  -H "Content-Type: application/json" \
  -d '{"name":"Buddy","species":"Dog","breed":"Golden Retriever","age":3,"photoUrl":"https://example.com/buddy.jpg"}'
```

### 2. Make Pet Available

```shell
curl -X PUT http://localhost:9000/pets/{petId}/make-available
```

### 3. Browse Available Pets

```shell
curl http://localhost:9000/pets/available
curl http://localhost:9000/pets/available?species=Dog
```

### 4. Add to Shopping Cart

```shell
curl -X POST http://localhost:9000/carts/my-cart/add \
  -H "Content-Type: application/json" \
  -d '{"petId":"{petId}"}'
```

### 5. Start Adoption

```shell
curl -X POST http://localhost:9000/adoptions \
  -H "Content-Type: application/json" \
  -d '{"cartId":"my-cart"}'
```

### 6. Confirm Adoption

```shell
curl -X PUT http://localhost:9000/adoptions/{adoptionId}/confirm \
  -H "Content-Type: application/json" \
  -d '{"adopterName":"Jane Doe","adopterContact":"jane@example.com"}'
```

### 7. Chat with AI Assistant

```shell
curl -X POST http://localhost:9000/assistant/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"my-session","message":"I have a big yard and kids, what pet do you recommend?"}'
```

### 8. Open the Website

Navigate to `http://localhost:9000/` in your browser.

## Configuration

Set the OpenAI API key for the AI assistant:

```shell
export OPENAI_API_KEY=your-key-here
```