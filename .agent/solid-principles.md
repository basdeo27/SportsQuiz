# SOLID Design Principles

Reference: https://en.wikipedia.org/wiki/SOLID

## Single Responsibility Principle (SRP)
- Every class should have **one reason to change**.
- If you find yourself writing "and" when describing what a class does, it should be split into two classes.
- A `UserService` should handle user business logic. It should not also handle sending emails or writing to a database directly.

## Open/Closed Principle (OCP)
- Classes should be **open for extension, closed for modification**.
- When behaviour needs to change, prefer adding a new class or implementation over editing existing ones.
- Use interfaces and abstract types to define contracts that can be extended without breaking existing consumers.

## Liskov Substitution Principle (LSP)
- Subtypes must be **substitutable** for their base types without altering correctness.
- If a function accepts an interface, any implementation of that interface must behave in a way the function can handle.
- Avoid overriding methods in a way that weakens preconditions or strengthens postconditions.

## Interface Segregation Principle (ISP)
- Prefer **many small, focused interfaces** over one large general-purpose interface.
- No class should be forced to implement methods it does not use.
- If you have a `Repository` interface with 10 methods but a consumer only needs `findById`, that interface is too broad.

## Dependency Inversion Principle (DIP)
- **Depend on interfaces, not concrete implementations.**
- High-level modules should not depend on low-level modules — both should depend on abstractions.
- Dependencies should be injected, never instantiated inside a class.

```kotlin
// ❌ Wrong - depends on concrete implementation
class UserService {
    private val repo = PostgresUserRepository()
}

// ✅ Correct - depends on interface
class UserService(private val repo: UserRepository)
```
