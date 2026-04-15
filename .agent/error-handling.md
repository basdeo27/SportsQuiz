# Error Handling

## Fail Fast
- Validate inputs **at the entry point** — the controller or service boundary — not deep in the call stack.
- Do not pass invalid state deeper into the system and handle it later.

## Domain Exceptions
- Define your own exception hierarchy rather than throwing generic exceptions.
- Exceptions should be meaningful to the domain.

```kotlin
// ❌ Wrong — generic, no context
throw IllegalArgumentException("not found")

// ✅ Correct — domain-specific
class QuizNotFoundException(id: String) : RuntimeException("Quiz not found: $id")
class InvalidScoreException(score: Int) : RuntimeException("Score cannot be negative: $score")
```

## Never Swallow Exceptions
- Do not catch an exception and do nothing with it.
- At minimum, log the exception before re-throwing or handling it.

```kotlin
// ❌ Wrong
try {
    repo.save(user)
} catch (e: Exception) { }

// ✅ Correct
try {
    repo.save(user)
} catch (e: Exception) {
    logger.error("Failed to save user: ${user.id}", e)
    throw UserPersistenceException(user.id, e)
}
```

## Consistent API Error Responses
- Errors that cross API boundaries must be mapped to a **standard response format**.
- Handle this in a centralised exception handler, not scattered across controllers.

```kotlin
// Standard error response shape
data class ErrorResponse(
    val status: Int,
    val code: String,
    val message: String
)
```

## Exception Layers
- **Controller layer**: Catches domain exceptions and maps them to HTTP responses.
- **Service layer**: Throws domain exceptions, never HTTP-specific ones.
- **Repository layer**: Wraps data-access exceptions in domain exceptions before re-throwing.
