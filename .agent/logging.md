# Logging

## Log Levels
Use the correct log level — do not default everything to `info` or `error`.

| Level   | When to use |
|---------|-------------|
| `DEBUG` | Detailed diagnostic information. Only useful during development or active debugging. Should not appear in production by default. |
| `INFO`  | Meaningful state changes — a request was received, a quiz was completed, a user was created. |
| `WARN`  | Something unexpected happened but the application can continue — a retry was triggered, a config value fell back to a default. |
| `ERROR` | A genuine failure that requires attention — an exception was caught, a downstream call failed, data could not be persisted. |

```kotlin
// ❌ Wrong — everything at info, no context
logger.info("error")
logger.info("User thing happened")

// ✅ Correct — appropriate level, useful context
logger.debug("Evaluating answer for quizId={} questionId={}", quizId, questionId)
logger.info("Quiz completed: quizId={} userId={} score={}", quizId, userId, score)
logger.warn("Score service slow, retrying: attempt={} quizId={}", attempt, quizId)
logger.error("Failed to persist quiz result: quizId={}", quizId, exception)
```

## Never Log Sensitive Data
- Do not log passwords, API keys, tokens, or personally identifiable information (PII).
- If logging a user-related event, log the user ID — never name, email, or other PII.

```kotlin
// ❌ Wrong
logger.info("User logged in: email={} password={}", email, password)

// ✅ Correct
logger.info("User authenticated: userId={}", userId)
```

## Structured Logging in Production
- Logs in production should be **structured (JSON)** to support querying and alerting in tools like CloudWatch, Datadog, or Grafana.
- Use a logging framework that supports structured output (e.g. Logback with `logstash-logback-encoder`).

## Include Useful Context
- Always include relevant IDs in log messages so a request can be traced through the system.
- Use parameterised logging — do not use string concatenation or interpolation in log statements (it evaluates even when the log level is disabled).

```kotlin
// ❌ Wrong — string interpolation always evaluates
logger.debug("Processing quiz $quizId for user $userId")

// ✅ Correct — parameterised, only evaluates if DEBUG is enabled
logger.debug("Processing quiz: quizId={} userId={}", quizId, userId)
```
