# Module Structure

## Directory Layout
Each feature or domain concept lives in its own self-contained module directory.

```
src/
└── {module-name}/
    ├── controller/        # HTTP entry points — parsing and response only
    ├── service/           # Business logic
    ├── repository/        # Data access layer
    ├── models/            # Domain models and data classes
    ├── properties/        # Configuration and environment properties
    └── fakes/             # Fake implementations for use in tests
```

## Rules

### Self-Containment
- Modules must **not import from each other directly**.
- If two modules need to share something, extract it into a `common/` or `shared/` module.
- Cross-module communication should happen via shared interfaces or events, not direct class references.

### Layer Responsibilities
- **Controller**: Handles HTTP concerns only — request parsing, input validation, and response mapping. No business logic.
- **Service**: Contains all business logic. Has no knowledge of HTTP, databases, or external frameworks.
- **Repository**: The only layer permitted to interact with the database or external data stores.
- **Models**: Plain data classes/value objects. No logic beyond simple validation or formatting.
- **Properties**: Typed wrappers around configuration values (environment variables, config files).

### Common Module
- Shared utilities, base interfaces, and cross-cutting types live in `common/`.
- Keep `common/` lean — if something only belongs to one module, it stays in that module.

```
src/
├── common/
│   ├── exceptions/
│   ├── models/
│   └── interfaces/
├── users/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── models/
│   ├── properties/
│   └── fakes/
└── quiz/
    ├── controller/
    ├── service/
    ├── repository/
    ├── models/
    ├── properties/
    └── fakes/
```
