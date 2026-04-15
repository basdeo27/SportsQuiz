# Naming Conventions

## General Rules
- Names should be **descriptive and self-documenting**. Prefer clarity over brevity.
- Avoid abbreviations unless they are universally understood (e.g. `id`, `url`, `http`).
- If a name needs a comment to explain it, the name is not good enough.

## Classes
- Use **nouns** that precisely describe what the class represents.
- Avoid vague suffixes like `Manager`, `Helper`, `Handler`, `Util`, `Processor`.

```kotlin
// ❌ Wrong — vague
class QuizManager
class DataHelper

// ✅ Correct — specific
class QuizScoreCalculator
class LeaderboardRanker
```

## Booleans
- Booleans should read as a **question** that can be answered yes or no.

```kotlin
// ❌ Wrong
val active: Boolean
val permission: Boolean

// ✅ Correct
val isActive: Boolean
val hasPermission: Boolean
val shouldRetry: Boolean
val isExpired: Boolean
```

## Functions and Methods
- Use **verbs** that describe what the function does.
- Query functions should start with `get`, `find`, `fetch`, or `calculate`.
- `find` implies the result may be null; `get` implies it always returns a value or throws.

```kotlin
fun getUser(id: String): User           // throws if not found
fun findUser(id: String): User?         // returns null if not found
fun calculateScore(answers: List<Answer>): Int
fun isEligibleForLeaderboard(user: User): Boolean
```

## Collections
- Collection variable names should be **plural**.

```kotlin
val users: List<User>
val quizResults: Map<String, Int>
```

## Constants
- Use `SCREAMING_SNAKE_CASE` for true constants.

```kotlin
const val MAX_ATTEMPTS = 3
const val DEFAULT_TIMEOUT_MS = 5000L
```

## Packages
- Lowercase, no underscores.
- Mirror the module structure: `com.funsportsquiz.quiz.service`
