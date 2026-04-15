# Testing Principles

## Fakes Over Mocks

Prefer **fakes** over mocks wherever possible.

- A **fake** is a real, working implementation of an interface designed for use in tests (e.g. an in-memory repository).
- A **mock** uses a mocking framework to stub and verify method calls.

Fakes are preferred because:
- They are more readable — test setup reads like real code, not framework magic.
- They are more maintainable — refactoring method signatures doesn't silently break assertions.
- They can be shared across many tests and used as a form of documentation.

### Example

```kotlin
// Interface
interface UserRepository {
    fun save(user: User): User
    fun findById(id: String): User?
}

// Fake — lives in src/users/fakes/
class FakeUserRepository : UserRepository {
    private val store = mutableMapOf<String, User>()

    override fun save(user: User): User {
        store[user.id] = user
        return user
    }

    override fun findById(id: String): User? = store[id]

    // Helper for test assertions
    fun all(): List<User> = store.values.toList()
}

// Test
class UserServiceTest {
    private val repo = FakeUserRepository()
    private val service = UserService(repo)

    @Test
    fun `saves user and returns it`() {
        val user = service.createUser("Dylan")
        assertNotNull(repo.findById(user.id))
    }
}
```

Mocks are acceptable when:
- You are testing interactions with a third-party library you cannot implement a fake for.
- The dependency is trivial and a fake would add no value.

## Test Naming
- Test names should describe **behaviour**, not implementation.
- Use backtick names in Kotlin for readability.

```kotlin
// ❌ Wrong
fun testGetUser()

// ✅ Correct
fun `returns null when user does not exist`()
fun `throws exception when score is negative`()
```

## Test Structure (Arrange / Act / Assert)
Every test should follow the AAA pattern with a blank line between each section:

```kotlin
@Test
fun `returns empty list when no results found`() {
    // Arrange
    val repo = FakeQuizRepository()
    val service = QuizService(repo)

    // Act
    val result = service.getResults("unknown-id")

    // Assert
    assertTrue(result.isEmpty())
}
```

## Testing Pyramid
- **Many unit tests** — fast, isolated, test one class at a time.
- **Fewer integration tests** — test module boundaries and database interactions.
- **Minimal end-to-end tests** — test critical user journeys only.

## General Rules
- Every class should be unit testable in isolation.
- Integration tests live in a separate source set and are clearly labelled.
- Tests must not share mutable state between test cases.
- No test should depend on the order in which tests run.
