# Immutability and State

## Prefer Immutability
- Use immutable data classes and value objects wherever possible.
- In Kotlin, prefer `val` over `var`. Default to immutable; make mutable only when necessary.
- Immutable objects are inherently thread-safe and easier to reason about.

```kotlin
// ❌ Wrong — mutable state, harder to reason about
data class QuizResult(var score: Int, var completedAt: Instant?)

// ✅ Correct — immutable value object
data class QuizResult(val score: Int, val completedAt: Instant)
```

## Avoid Shared Mutable State
- If two components need the same data, **pass it explicitly** — do not share a mutable reference.
- Shared mutable state is the root cause of most concurrency bugs.

## State Changes
- When an object's state needs to change, return a **new copy** rather than mutating in place.

```kotlin
// ✅ Kotlin data class copy pattern
val updated = result.copy(score = result.score + 10)
```

## Collections
- Prefer `List` over `MutableList`, `Map` over `MutableMap` in function signatures and class fields.
- Only expose mutable collections internally where strictly necessary, and never through a public API.

```kotlin
// ❌ Exposes mutable internal state
class ScoreTracker {
    val scores: MutableList<Int> = mutableListOf()
}

// ✅ Immutable public surface
class ScoreTracker {
    private val _scores: MutableList<Int> = mutableListOf()
    val scores: List<Int> get() = _scores.toList()
}
```
