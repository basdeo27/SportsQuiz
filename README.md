# Sports Quiz App

The quiz is live at [funsportsquiz.com](https://funsportsquiz.com).

## Backend (Spring Boot)

From the repo root:

```bash
make db-up
./gradlew bootRun
```

The API will be available at `http://localhost:8080`. The default profile is
`local`: scores and accounts use DynamoDB Local at `http://localhost:8000`
with placeholder credentials. No AWS credentials are needed. IntelliJ's plain
`bootRun` configuration uses the same defaults.

For development with in-memory storage (no database required):

```bash
QUIZ_STORAGE=local ./gradlew bootRun --args='--spring.profiles.active=local'
```

The `local` and `dev` profiles create a default account with username `user`
and password `user`. Its password is restored on each startup, while its ID
and existing results are preserved. This account is never seeded when the
`prod` profile is active. Normal account registration still requires at least
eight characters in a password. Email verification is disabled in development;
`user` / `user` works without a mail service. `user@localhost.test` / `user`
also works.

## Frontend (React + Vite)

From the repo root:

```bash
cd frontend
npm install
npm run dev
```

Vite runs at `http://localhost:5173` and proxies `/v0` requests to the backend at `http://localhost:8080`.

### Frontend API base URL

The frontend uses `/v0` through the local Vite proxy. Production also uses
relative `/v0` URLs: the deployment workflow configures CloudFront to route
API calls to the backend, keeping secure login cookies on the frontend domain.
The GitHub `VITE_API_BASE_URL` secret is used as that backend origin, not as a
browser API URL. Backend daily media is routed under `/assets/daily/*`.

## Audio hint preparation

Run `make audio-setup` once, then:

```bash
make audio-clip URL="https://www.youtube.com/watch?v=VIDEO_ID" START="1:24" END="1:38" PLAYER="brady"
```

Creates an MP3, caches the download and prints its hint JSON reference. Local
MOV/video files work with `INPUT="/path/to/recording.mov"` instead of `URL`.
See [setup, options and media publishing](docs/audio-clips.md).

## Local NoSQL (DynamoDB Local)

Requires Docker. From the repo root:

```bash
make db-up
```

Stop it with:

```bash
make db-down
```

Start the backend against DynamoDB Local:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Alternatively, `make dev-backend` starts both DynamoDB Local and the backend.
Questions continue to load from local JSON. Production containers explicitly
set `SPRING_PROFILES_ACTIVE=prod` to use AWS DynamoDB. Outside Docker, select
production explicitly with `--args='--spring.profiles.active=prod'`.

## Daily leaderboard and history

Daily scores are stored once per participant and challenge date. Member scores
are saved automatically. Ranked play requires login before starting and permits
one persisted attempt per account/day. Refreshing or restarting resumes that
attempt without resetting the timer or hints. By default, guests can also play
today's challenge without an account, with an unranked result. Anonymous users
can practise past challenges; unranked scores cannot be submitted to the leaderboard,
including after signing in. Sign in before starting to enter the rankings.
Giving up records a completed zero-point result.

Today's challenge always falls back to a date-seeded random pick from the authored
daily pool when no entry is scheduled. Everyone receives the same pick for that date
and catalog, and ranked scores are recorded against today. During launch, production's
Practice picker also permits current/future authored dates, with unranked results.
Set `DAILY_ALLOW_PRACTICE_DATE_OVERRIDE=false` to restore past-only practice.
See [daily authoring](docs/daily-hints.md#live-daily-and-launch-previews) for details.

To require login for today's challenge, set `DAILY_LOGIN_REQUIRED=true`
(Spring property `quiz.daily.attempts.login-required`). The default is `false`.
Restart the backend after changing it; the frontend follows the server response
without a separate frontend flag or rebuild. For automated production releases,
set the `DAILY_LOGIN_REQUIRED` GitHub repository/environment variable and deploy.

The DynamoDB `sports-quiz-daily-scores` table supports direct user/date lookups
and paged user history. Its `daily-ranking` index serves the daily leaderboard
without scanning history. The index is maintained by DynamoDB and may briefly
lag behind a submission. Missing tables are created on startup; the backend's
IAM role needs access to this table and its index.

Set `DAILY_LEADERBOARD_PAGE_SIZE` to change the default batch size (50).
`quiz.daily.leaderboard.max-page-size` sets the request limit (100 by default).
Frontend leaderboard and history requests use the backend default. Both return
a cursor for loading the next page.

Production uses email + password login and verified email addresses. Signup
sends a single-use verification link through AWS SES; the user confirms their
password to activate the account. Existing username accounts can link an email
without losing their ID or scores. Browser login uses HttpOnly cookies and
Dynamo-backed sessions, surviving refreshes and backend restarts. Password
changes revoke existing sessions.

Basic monitoring records endpoint errors and quiz completions. In local/dev:

```bash
curl -H 'X-Monitoring-Key: dev-monitoring' http://localhost:8080/v0/monitoring
```

Production requires a `MONITORING_API_KEY` secret of at least 32 characters.
The summary reports persistent totals and daily counts by quiz type, plus
endpoint error counts by HTTP status. Tracking starts with this release.

See [.agent/daily-leaderboard.md](.agent/daily-leaderboard.md) for the API/schema,
and [production rollout notes](docs/production-readiness.md) for SES setup,
required permissions, verification and rollback. These changes do not deploy
or configure live AWS resources automatically.

## Docker

Build the image (linux/amd64):

```bash
docker build --platform=linux/amd64 -t sports-quiz-backend .
```

Or use the helper script:

```bash
./scripts/build_docker.sh [tag]
```

Run locally:

```bash
docker run --rm -p 8080:8080 sports-quiz-backend
```

## Automatic deployments

PRs targeting main run backend tests and build both apps. Merging to main
then publishes an immutable commit-tagged ECR image and deploys the backend
and matching frontend automatically. Releases have saved artifacts, smoke
checks, failure rollback and a manual rollback action.

One-time OIDC, ECR/Lightsail and GitHub configuration is described in
[deployment setup](docs/deployment.md). Infrastructure setup runs separately;
no live deployment is performed by changing these files locally.

## Manual ECR push script

```bash
./scripts/push_ecr.sh <aws_account_id> <aws_region> <ecr_repo_name> [image_tag]
```

## S3 frontend push script

```bash
./scripts/push_s3_frontend.sh <aws_region> <s3_bucket> <cloudfront_distribution_id> [api_base_url]
```


We got our images from espn: https://site.api.espn.com/apis/site/v2/sports/hockey/nhl/teams
