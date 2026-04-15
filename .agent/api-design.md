# API Design

## Layer Responsibilities

### Controller
- Handles **HTTP concerns only**: parsing request bodies, extracting path/query parameters, setting response codes, and mapping to response DTOs.
- Contains **no business logic**.
- Delegates everything to the service layer.
- Maps domain exceptions to appropriate HTTP responses via a centralised exception handler.

```kotlin
// ✅ Thin controller
@PostMapping("/quiz/{quizId}/submit")
fun submitQuiz(
    @PathVariable quizId: String,
    @RequestBody request: SubmitQuizRequest
): ResponseEntity<QuizResultResponse> {
    val result = quizService.submit(quizId, request.answers)
    return ResponseEntity.ok(QuizResultResponse.from(result))
}
```

### Service
- Contains all **business logic**.
- Has no knowledge of HTTP — does not reference `HttpServletRequest`, `ResponseEntity`, or HTTP status codes.
- Accepts and returns domain models, not request/response DTOs.

### Repository
- The **only layer** that interacts with the database or external data stores.
- Returns domain models, never raw database rows or ORM entities exposed to the service layer.

## Request and Response DTOs
- Use separate classes for API input (`Request`) and output (`Response`) — do not expose domain models directly over the API.
- This decouples your API contract from your internal domain model.

```kotlin
data class SubmitQuizRequest(val answers: List<String>)
data class QuizResultResponse(val score: Int, val rank: Int) {
    companion object {
        fun from(result: QuizResult) = QuizResultResponse(result.score, result.rank)
    }
}
```

## HTTP Status Codes
- `200 OK` — successful read or update.
- `201 Created` — resource successfully created.
- `400 Bad Request` — invalid input from the client.
- `404 Not Found` — resource does not exist.
- `409 Conflict` — state conflict (e.g. quiz already submitted).
- `500 Internal Server Error` — unexpected server failure.

## Consistency
- Resource names in URLs should be **plural nouns**: `/quizzes`, `/users`, `/scores`.
- Use kebab-case for multi-word URL segments: `/quiz-results`.
- Avoid verbs in URLs — the HTTP method conveys the action.

```
// ❌ Wrong
POST /submitQuiz
GET  /getUser

// ✅ Correct
POST /quizzes/{quizId}/submissions
GET  /users/{userId}
```
