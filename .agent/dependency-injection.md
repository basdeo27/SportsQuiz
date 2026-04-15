# Dependency Injection

## Core Rules
- All dependencies must be **injected**, never instantiated inside a class.
- Use **constructor injection** as the default. Avoid field or setter injection.
- Avoid static state and singletons — they make testing harder and hide dependencies.

```kotlin
// ❌ Wrong — hidden dependency, untestable
class QuizService {
    private val repo = PostgresQuizRepository()
    private val clock = SystemClock()
}

// ✅ Correct — explicit dependencies, fully testable
class QuizService(
    private val repo: QuizRepository,
    private val clock: Clock
)
```

## Depend on Interfaces
- Inject interfaces, not concrete types.
- This allows fakes to be injected in tests and real implementations in production.

```kotlin
// ✅ Interface injected — can swap implementations freely
class LeaderboardService(
    private val scoreRepository: ScoreRepository,
    private val cache: CacheProvider
)
```

## Wiring
- Wire all dependencies at the **application boundary** — the top-level application configuration or DI module.
- Business logic classes must have no knowledge of how they are wired together.
- Avoid calling a DI container (e.g. `ApplicationContext.getBean()`) from inside a service or repository.

## Avoid
- `companion object` instances used as service locators.
- `object` singletons that hold mutable state.
- Passing a DI container into a class so it can resolve its own dependencies.
