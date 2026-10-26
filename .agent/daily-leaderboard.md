# Daily challenges, authentication and basic monitoring

This file supersedes the historical runtime details in README.md for daily challenges/accounts.

## Profiles

Plain `./gradlew bootRun` uses the default `local` profile and DynamoDB Local at localhost:8000. Placeholder credentials are supplied by application-local.yaml. Run `make db-up` first. `QUIZ_STORAGE=local` switches persistence to in-memory test/development implementations.

Both local/dev disable email verification, enable the development user/user login, use non-Secure SameSite=Lax cookies over localhost HTTP, and allow date overrides for testing. The seeded user also has user@localhost.test as an email login. Production explicitly sets SPRING_PROFILES_ACTIVE=prod, requires verified email login, disables the username development bypass, restricts dates and sets Secure cookies. Never activate local/dev with prod.

## Login and verification

Production registration accepts `{email,password,nickname}`; email is normalized to lowercase. The accounts table has canonical account items and an `EMAIL#<normalized email>` lookup item. Creating both in a transaction enforces uniqueness and supports GetItem email login without scans.

Production registration returns an unverified account without a session. A verification link is sent through AWS SES from MAIL_FROM (default noreply@funsportsquiz.com). SES sender/domain verification, appropriate SES permissions and production sending access must be configured in the chosen AWS region. No live AWS changes or emails were sent during implementation.

Verification links expire after 30 minutes. Only token hashes are stored, verification requires confirming the account password, and a token is consumed once. Resends have a per-email 60-second cooldown plus endpoint rate limiting. Successful verification signs the user in. Local/dev registration is immediately verified and sends no mail. LocalEmailSender is a test/development outbox when explicitly testing verification locally; it is never selected in prod.

Existing username accounts use POST /v0/account/link-email with `{username,password,email}`, then verify the email. The account ID and scores remain unchanged. Normal production login does not fall back to username.

Browser login uses an HttpOnly cookie. Sessions last 24 hours and are stored in `sports-quiz-auth-sessions` with a SHA-256 token verifier, account ID, credential fingerprint, purpose and expiry. Password changes/deleted accounts invalidate existing sessions. Logout revokes the session and clears the cookie. GET /v0/account/me restores identity on page refresh. Bearer tokens remain available to non-browser API clients; browser requests send X-Session-Mode:cookie, so the token is omitted from response JSON.

Cookie-authenticated writes require X-CSRF-Protection:1, and unsafe requests from unapproved origins are rejected. The browser API wrapper sends this header and credentials automatically. Responses use Cache-Control:no-store. Production API traffic is first-party through CloudFront /v0/*; Secure SameSite=Lax cookies work without third-party cookies. Do not switch production API traffic to an unrelated backend hostname without redesigning the cookie policy.

## Ranked and practice attempts

POST /v0/daily accepts `{date?,practice?:false}`. In production:

- Ranked play requires a verified logged-in account and today's challenge.
- Guest play of today's challenge is enabled by default with `quiz.daily.attempts.login-required=false` / `DAILY_LOGIN_REQUIRED=false`. Set it to `true` and restart/redeploy the backend to restore mandatory login for today's challenge. The frontend starts via the API and opens login on HTTP 401, so there is no separate frontend toggle.
- During launch, `quiz.daily.attempts.allow-practice-date-override` defaults to true in prod (`DAILY_ALLOW_PRACTICE_DATE_OVERRIDE`); anonymous practice and GET /v0/daily/dates include all authored dates. Set false to restore past-only practice/archive dates. This does not enable ranked date overrides or development login.
- Practice results cannot be claimed or submitted to the leaderboard after login.

Today's explicit schedule takes priority. If it is missing, DailyRepository chooses from the authored JSON pool with a date-seeded random pick and copies the entry to the live London date. The same date/catalog is stable across instances and restarts. Research notes never enter the pool. Scores and ranked IDs use today, not the original content date. Pool changes can change a fallback pick for new attempts, so avoid mid-day catalog changes or explicitly schedule today. Persisted attempts retain their content snapshots.

Guest daily attempts have random IDs, `ranked=false`, no account ownership, and the same persisted scoring, 24-hour expiry and monitoring path as archive practice. They can reveal hints, answer, give up and resume by session ID, but cannot be promoted to ranked results after login. Logged-in non-practice starts remain one canonical ranked attempt per account/day, regardless of the toggle. Existing unranked sessions may finish after the toggle is changed; the switch controls new daily starts. Completion/share UI labels both guest and practice results as unranked. Guest session IDs are not automatically restored by the frontend after a refresh.

`sports-quiz-daily-attempts` stores a complete server-side attempt snapshot: start time, revealed hints, ownership, challenge version, completion and final score. Ranked ID is `RANKED#<account ID>#<date>`; conditional creation gives one canonical attempt per account/day. Optimistic version checks protect concurrent mutations across instances. Starting again resumes the same attempt. The final score is frozen before persistence and retries finish an idempotent result write.

GET /v0/daily/sessions/{encodedSessionId} restores an owned attempt. The browser stores only the ranked attempt identifier as navigation metadata in sessionStorage, never login credentials. Active/completed attempts and login sessions survive Dynamo-backed restarts. Timer keeps running while the page is closed. Practice attempts expire after 24 hours; ranked records are retained to prevent retry resets. The normal logo/face quiz sessions remain process-local.

Existing pre-persistence daily scores remain final: they are loaded as completed attempts rather than allowing another ranked attempt. Giving up records a zero-point completion. Repeated completion/submission returns the canonical score.

## Authoring hints

See docs/daily-hints.md for the reusable ACCOMPLISHMENTS hint type, optional TEXT attribution, local image convention and the 2026-10-24 Markus Näslund challenge. Accomplishment cards are driven by names/counts/details and optional gold/silver/bronze breakdowns; future players do not require custom display code.

## Score storage and pagination

`sports-quiz-daily-scores` stores one canonical item per participant/date:

- partition participantId: USER#accountId
- sort challengeDate: ISO date
- daily-ranking GSI: partition challengeDate, sort rankingKey
- rankingKey: inverted score, completion timestamp, participant ID

History is a descending strongly consistent base-table Query. Own score on a date is a strongly consistent GetItem. Daily standings use a GSI Query, highest score first, ties by earliest finish. GSI propagation is eventual. Old anonymous rows are excluded; filtered empty pages follow LastEvaluatedKey until a member page is filled or data is exhausted. No full-table leaderboard scans or offset pagination.

Default page size: quiz.daily.leaderboard.page-size / DAILY_LEADERBOARD_PAGE_SIZE (50). Maximum: max-page-size (100). Versioned cursors are bound to the date/user. Clients omit limit to use the configured default. Ranking is live rather than a snapshot; Refresh restarts pagination.

Endpoints:

- POST /v0/daily (start/resume)
- GET /v0/daily/sessions/{sessionId}
- POST /v0/daily/hint, /answer, /give-up
- POST /v0/daily/scores {sessionId} (retry ranked result persistence only)
- GET /v0/daily/leaderboard?date=&limit=&cursor=
- GET /v0/daily/history?limit=&cursor= (authenticated user only)
- GET /v0/daily/scores/me?date= (authenticated user only)

Regular quiz ownership also comes from authentication, never a supplied accountId/userId. Member quiz answers/skips/review require ownership; anonymous custom quizzes remain playable. User quiz history requires authentication.

## Monitoring

GET /v0/monitoring?date=YYYY-MM-DD with X-Monitoring-Key returns persisted all-time completion counts, counts on that date and endpoint error counts grouped by method, route template and HTTP status. LOGO, FACE, DAILY_RANKED and DAILY_PRACTICE are reported separately. DAILY_PRACTICE includes unranked guest daily completions as well as archive practice. Giving up is a completed daily challenge. Duplicate completion events do not increment counters again.

Production monitoring is disabled unless MONITORING_API_KEY is set (minimum 32 characters). Local/dev use dev-monitoring. The monitoring response is private/no-store. Counts start with this release, not a retroactive backfill of historic results; trackingSinceMillis identifies the first recorded completion. The Dynamo monitoring table uses atomic transactions with deduplication marker items. Error counters update atomically. Monitoring failures are logged and do not fail the player's request; monitoring is best-effort around outages, not an accounting ledger.

Request logs contain JSON events with request ID, endpoint path, status and duration, and no query strings, credentials, headers or request bodies. 5xx requests log at ERROR; 4xx are distinguishable by status/outcome. X-Request-ID is returned for support/debugging. Public production Actuator exposure is limited to health. No external error alerts/dashboard/collector has been configured by this change.

## Deployment and infrastructure

CI runs tests with QUIZ_STORAGE=local. Opt-in Dynamo integration tests require DYNAMODB_TEST_ENDPOINT=http://localhost:8001 (loopback only). Scripts/tests exercise CloudFront config generation without touching AWS.

The build/deploy workflow tests and builds both apps before deploying main, publishes immutable commit-tagged private ECR images, assumes an OIDC deployment role and deploys the same frontend artifact after backend smoke checks. S3 release manifests support rollback without rebuilding. Infrastructure setup is a separate manual workflow: configure_api_proxy.sh preserves the distribution and configures /v0/* and /assets/daily/* with disabled caching and forwarded cookies/headers. The frontend builds with an empty API base. VITE_API_BASE_URL is an infrastructure-origin fallback only. See docs/deployment.md for variables, permissions, release sequencing and rollback.

Required application permissions: DynamoDB read/write/transactions, DescribeTable/CreateTable and DescribeTimeToLive/UpdateTimeToLive for its tables; Query on the score index; SES SendEmail on the configured identity. Single-key session/attempt/monitoring tables are created automatically with expiresAtSeconds TTL. Account/result/score tables keep existing schema. Infrastructure-managed deployments should provision these before starting.

Required deployment permissions additionally include CloudFront GetDistribution/GetDistributionConfig/UpdateDistribution and invalidation, plus existing Lightsail/S3 operations. Add a MONITORING_API_KEY GitHub secret to enable production summaries. Verify SES sender and exit SES sandbox before inviting public users. See docs/production-readiness.md for remaining operational work and rollback.

All-time rankings, password recovery, admin moderation, a full content publishing process, distributed rate limits and alert delivery remain separate work.

Daily media now ships in the frontend release under content-hashed `/media/daily/` S3 keys. `npm run build` validates/packages it using scripts/prepare_daily_media.py (Python 3.9+). The production DailyMediaResolver rewrites only revealed LOCAL hints to REMOTE CDN URLs using quiz.daily.media.base-url (DAILY_MEDIA_BASE_URL, default /media/daily); local/dev remain on backend media. Releases upload media before backend activation and retain previous objects for rollback. JSON source references stay portable. See docs/daily-hints.md.

## Sharing results

frontend/src/ResultShare.tsx renders an SVG result card and prepares a 1440×880 PNG locally. Completion pages for daily, logo and face games offer Share result, Copy text and Save image. Daily cards show a spoiler-free hint strip (yellow revealed clues, green solving clue, grey unrevealed), date, final score, hints and elapsed time; practice and unsolved attempts are labelled. Shares contain only public result metrics and the canonical site URL, never answers, clue content, account details or session identifiers. Sharing happens only through a player action. Unsupported/blocked clipboard access exposes selectable text, and native share cancellation is silent.

DailyStartResponse and DailyGiveUpResponse now include elapsedSeconds so completed/resumed/practice results keep the server's frozen completion time even if no leaderboard score exists. DailyAnswerResponse already supplied it. Custom quizzes use server review metrics and a London completion date captured on finishing. No media uploads or share-service API is needed. Browser share/file support varies; image generation is prepared before the click to retain transient activation. See MDN Web Share API and Clipboard writeText documentation for browser requirements.
