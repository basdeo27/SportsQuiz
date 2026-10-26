# Production rollout notes

This change prepares durable ranked attempts, verified email authentication, deployment checks and basic endpoint/usage monitoring. It does not deploy resources or verify an email domain automatically.

## Before rollout

1. Verify `funsportsquiz.com` or `noreply@funsportsquiz.com` in AWS SES in the configured region (default eu-west-2), publish the required DNS records, and request production sending access if the account is sandboxed. Grant the application `ses:SendEmail` for that identity. `MAIL_FROM`, `AWS_REGION` and `FRONTEND_URL` can override the defaults. No live test mail was sent by Codex.
2. Grant table read/write, transaction and startup-provisioning permissions for accounts, quiz results, daily scores/index, auth sessions, daily attempts and monitoring. The new table names default to sports-quiz-auth-sessions, sports-quiz-daily-attempts and sports-quiz-monitoring. Enable DynamoDB point-in-time recovery through your infrastructure and perform a restore test; this code does not change backup settings.
3. Configure the OIDC deployment role and GitHub variables described in [deployment setup](deployment.md), then run the separate infrastructure setup workflow once. Routine main releases publish to ECR and deploy both artifacts; they do not update CloudFront routing. Browser API calls use relative /v0 URLs with forwarded cookies and disabled caching.
4. Set a random `MONITORING_API_KEY` GitHub secret of at least 32 characters if the summary endpoint is required. Do not put it in a URL or frontend bundle.
5. Prepare actual current/upcoming daily content. The repository's sample daily data is not a complete release schedule. Production intentionally blocks playing future challenges and practising today's challenge.

## Development

`make db-up`, then `./gradlew bootRun`; frontend `cd frontend && npm run dev`.

Login `user` / `user` remains available in local/dev; verification is disabled. New accounts still collect email and sign in immediately. No SES credentials or real mail delivery are needed for development. Do not activate development profiles on the production container.

## Basic monitoring

Local summary:

```bash
curl -H 'X-Monitoring-Key: dev-monitoring' http://localhost:8080/v0/monitoring
```

Production (key supplied by your shell environment):

```bash
curl -H "X-Monitoring-Key: $MONITORING_API_KEY" 'https://funsportsquiz.com/v0/monitoring?date=2026-10-24'
```

The response includes all-time and daily completion counts by quiz type and endpoint errors separated by HTTP status. Counts start when this feature is deployed; they do not scan/backfill previous results. Counts are deduplicated and survive backend restarts with DynamoDB. Gave-up daily attempts count as completed with zero points. Endpoint errors are grouped by route template to avoid one series per quiz/session ID; 4xx client errors and 5xx server errors are separate entries.

Logs include a request ID (also in X-Request-ID), status, duration and endpoint path, with query strings omitted. Search status >= 500 for server errors. Monitoring writes are best-effort and do not prevent quiz completion when unavailable; failures produce ERROR logs for diagnosis.

No notification delivery or external dashboard is configured yet. Future small additions: an uptime/error-rate alert and notifications for failed score saves/email delivery or low daily-content supply.

## Release verification

- Verify production profile, secure HttpOnly cookie, first-party API routing and a real verification email.
- Create/verify an account, refresh the page and confirm login remains active.
- Start the ranked challenge, reveal a hint, refresh and confirm the same timer/hints resume.
- Complete it, restart the backend and confirm the same result and history remain.
- Confirm anonymous users cannot start ranked play or submit practice results; archived practice remains available.
- Check the protected monitoring summary after a completion and a deliberately invalid API request.
- Check a real mobile browser. Visual/mobile browser verification was unavailable in the agent environment.

Automated checks:

```bash
QUIZ_STORAGE=local ./gradlew test
python3 -m unittest discover -s scripts/tests -v
cd frontend && npm run build
```

For isolated real DynamoDB tests, run DynamoDB Local on port 8001 and set `DYNAMODB_TEST_ENDPOINT=http://localhost:8001` while running Gradle tests. Tests use unique disposable table names and delete only those tables.

## Rollback

Lightsail keeps the previous deployment active if a new one fails to start. The workflow waits for the exact new version and checks health/API responses; frontend release waits for backend success. Recorded releases support automatic failure rollback and the manual Roll back production workflow, restoring a prior ECR image and matching frontend without rebuilding. See [deployment setup](deployment.md). Restore the prior CloudFront configuration if reverting API routing. Account email lookup items are additive; old code can read canonical account records, but old authentication code does not understand the new persisted sessions. Users may need to log in again after an authentication rollback.

## Remaining work

Password recovery, email-change workflows, content scheduling/validation, backups/restore verification, external alerts, distributed abuse controls, moderation and full mobile/device checks remain outside this change. Normal custom quiz sessions still live in memory; daily ranked attempts and login sessions use durable storage. One-account-per-day is enforced; multiple accounts, sharing answers and external research are not prevented.
