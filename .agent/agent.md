# Sports Quiz Agent Handoff

This repository contains a sports logo quiz application with:

- a Spring Boot backend in Kotlin
- a React + Vite frontend in TypeScript
- league/team data stored as JSON under `src/main/resources/data`
- optional DynamoDB support for selected persistence concerns

This file is intended to let a new agent recover context quickly without rereading the full repo.

## Product Summary

The app generates logo quizzes across multiple leagues. The user:

1. lands on a welcome screen
2. starts a new quiz
3. chooses question count, leagues, and difficulty
4. answers logo questions one at a time
5. can skip questions
6. can reveal a hint locally in the frontend
7. sees a completion screen with score
8. can review each question, their answer, and the correct answer

Supported leagues currently:

- NBA
- MLB
- NHL
- NFL
- EPL

## High-Level Architecture

### Backend

Backend code is under `src/main/kotlin/com/elliotmoose/Sports/Quiz/`.

Package layout:

- `api/`: controllers and exception handling
- `service/`: core quiz logic
- `model/`: request/response/domain models
- `repository/`: question/result storage implementations
- `config/`: Spring configuration and properties

### Frontend

Frontend lives under `frontend/`.

Stack:

- React
- TypeScript
- Vite

The frontend is a single-screen-flow app controlled by local component state in `frontend/src/App.tsx`.

### Data

Static quiz data now lives under two subdirectories:

- `src/main/resources/data/logos/`
- `src/main/resources/data/faces/`

Current logo files:

- `src/main/resources/data/logos/nba.json`
- `src/main/resources/data/logos/mlb.json`
- `src/main/resources/data/logos/nhl.json`
- `src/main/resources/data/logos/nfl.json`
- `src/main/resources/data/logos/epl.json`

Current face files:

- `src/main/resources/data/faces/nba.json`
- `src/main/resources/data/faces/nhl.json`
- `src/main/resources/data/faces/mlb.json`
- `src/main/resources/data/faces/nfl.json`

Each logo-team entry includes:

- `id`
- `name`
- `logoUrl`
- `answers`
- `hint`

The NBA faces data currently contains player entries with:

- `id`
- `name`
- `team`
- `teamId`
- `headshotUrl`
- `answers`
- `isAllStar`

`src/main/resources/data/faces/nba.json` is the full flat NBA player list.

`src/main/resources/data/faces/nhl.json` is the full flat NHL player list.

`src/main/resources/data/faces/mlb.json` is the full flat MLB player list.

`src/main/resources/data/faces/nfl.json` is the full flat NFL player list.

The face datasets no longer duplicate per-team files. Instead, each player now carries `teamId`, which matches the corresponding team id in the logo dataset for that league. Future filtering should use the flat file plus `teamId`.

## Current Runtime Model

The application has two distinct storage concerns:

1. question source
2. persisted quiz results

They are configured independently.

### Question storage

Controlled by:

- `quiz.questions.storage`

Supported values:

- `local`
- `dynamo`

Current intended mode: `local`

Questions are not authored through an API, so local JSON is the current source of truth.
The current logo quiz still reads from `data/logos/...`.

### Result storage

Controlled by:

- `quiz.results.storage`

Supported values:

- `local`
- `dynamo`

Results are persisted when a quiz completes. Right now all results belong to a single hardcoded user id: `single-user`.

### Important limitation

The active quiz session itself is still in-memory inside `QuizService`.

`QuizService` keeps active quizzes in:

- `quizzes: ConcurrentHashMap<String, Quiz>`
- `attemptsByQuiz: ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>`

This means:

- creating a quiz stores it only in process memory
- answering/skipping updates only in process memory
- `GET /v0/quiz/{quizId}` works only while that process still has the quiz in memory
- only the final summarized `QuizResult` is persisted through `ResultRepository`

If the service restarts, active quizzes are lost.

## Core Backend Flow

### `QuizService`

Primary file:

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/service/QuizService.kt`

Behavior:

- validates quiz length against config min/max
- loads all available questions for selected leagues from `QuestionRepository`
- shuffles using `SecureRandom` via `Collections.shuffle`
- takes the first N questions
- stores the quiz in memory

Answer handling:

- `EASY`
  - fuzzy matching
  - user gets 2 attempts
  - first miss does not advance
  - second miss advances and counts wrong
- `MEDIUM`
  - normalized exact match against any accepted answer
- `HARD`
  - normalized exact match against the full team name only

Skip handling:

- marks question skipped
- records whether hint was used
- advances immediately

Completion:

- a quiz is complete when every question is either:
  - answered with a final correctness result, or
  - skipped
- completion timestamp is set
- a summarized `QuizResult` is saved once through `ResultRepository`

### Scoring

Scoring logic is in:

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/model/QuizScoring.kt`

Current formula:

- accuracy = `correctCount / totalQuestions`
- pace = `totalQuestions / elapsedSeconds`
- score = `(1000 + totalQuestions * 10 + pace * 1000) * accuracy`

This intentionally rewards:

- more correct answers
- more questions completed
- faster completion

## API Surface

Primary controller:

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/api/SportsQuizController.kt`

Endpoints:

- `POST /v0/quiz`
  - creates a quiz
  - returns `quizId` plus question list
- `POST /v0/quiz/answer`
  - submits an answer
  - returns correctness, normalized answer, matched answer, correct answer, remaining attempts, and whether the UI should advance
- `POST /v0/quiz/skip`
  - skips a question
  - returns skipped flag plus correct answer
- `GET /v0/quiz/{quizId}`
  - returns the in-memory quiz review payload
- `GET /v0/quiz/results`
  - returns persisted summarized results for the single user

Errors:

- `IllegalArgumentException` is mapped to `400` in `src/main/kotlin/com/elliotmoose/Sports/Quiz/api/ApiExceptionHandler.kt`

### Hint behavior

There is no hint API anymore.

Hints are embedded in question data and returned as part of quiz creation.

Current flow:

- backend includes `hint` in `QuizQuestion`
- frontend reveals it locally when the user clicks Hint
- answer/skip requests send `hintUsed: true/false`
- backend stores hint usage on the in-memory `Question`
- review payload exposes whether each question was hinted

## Repository Split

### Question repository

Interface:

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/repository/QuestionRepository.kt`

Implementations:

- `LocalQuizRepository`
- `DynamoQuizRepository`

#### `LocalQuizRepository`

File:

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/repository/LocalQuizRepository.kt`

Behavior:

- loads all league JSON files from `src/main/resources/data`
- maps `TeamEntry` to `Question`
- uses the JSON `hint` if present
- falls back to generated hint text if `hint` is missing

#### `DynamoQuizRepository`

File:

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/repository/DynamoQuizRepository.kt`

Behavior:

- queries the teams table by `league`
- maps Dynamo items to `Question`
- also falls back to generated hints if `hint` is missing

This exists, but the intended current mode for questions is still local JSON.

### Result repository

Interface:

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/repository/ResultRepository.kt`

Implementations:

- `LocalResultRepository`
- `DynamoResultRepository`

#### `LocalResultRepository`

File:

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/repository/LocalResultRepository.kt`

Behavior:

- stores summarized `QuizResult` objects in an in-memory `ConcurrentHashMap`

#### `DynamoResultRepository`

File:

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/repository/DynamoResultRepository.kt`

Behavior:

- writes summarized results to Dynamo
- queries by `userId`
- sorts descending by `completedAtMillis`

## Config Model

Primary config file:

- `src/main/resources/application.yaml`

Important properties:

- `quiz.questions.storage`
- `quiz.results.storage`
- `quiz.settings.min-questions`
- `quiz.settings.max-questions`
- `quiz.cors.allowed-origins`
- `quiz.dynamo.endpoint`
- `quiz.dynamo.region`
- `quiz.dynamo.teams-table-name`
- `quiz.dynamo.results-table-name`
- `quiz.dynamo.seed`
- `quiz.dynamo.access-key`
- `quiz.dynamo.secret-key`

### Current notable values

At the time of writing:

- Spring profile is set to `prod` in `application.yaml`
- CORS allows:
  - `https://d2323rgqey2wlv.cloudfront.net`
  - `http://localhost:5173`
- min questions is currently `1`
- max questions is currently `25`
- question storage is `local`
- result storage is `local`

### Property classes

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/config/QuizSettingsProperties.kt`
- `src/main/kotlin/com/elliotmoose/Sports/Quiz/config/QuizStorageProperties.kt`
- `src/main/kotlin/com/elliotmoose/Sports/Quiz/config/QuizDynamoProperties.kt`
- `src/main/kotlin/com/elliotmoose/Sports/Quiz/config/QuizCorsProperties.kt`

### Dynamo credentials

`application.yaml` is wired to read:

- `AWS_ACCESS_KEY`
- `AWS_SECRET_CODE`

into:

- `quiz.dynamo.access-key`
- `quiz.dynamo.secret-key`

If those are blank, `DynamoConfig` falls back to the default AWS credentials provider chain.

Note that the env var name currently used is `AWS_SECRET_CODE`, not the more typical `AWS_SECRET_ACCESS_KEY`.

## DynamoDB Notes

Config files:

- `src/main/kotlin/com/elliotmoose/Sports/Quiz/config/DynamoConfig.kt`
- `src/main/kotlin/com/elliotmoose/Sports/Quiz/config/DynamoSeeder.kt`

Behavior:

- `DynamoConfig` always creates a `DynamoDbClient`
- optional static credentials are used only if both access key and secret key are present
- custom endpoint override is supported

`DynamoSeeder` responsibilities:

- ensure results table exists if result storage is `dynamo`
- ensure teams table exists and seed team data if:
  - question storage is `dynamo`
  - `quiz.dynamo.seed=true`

Current table design:

- teams table:
  - partition key: `league`
  - sort key: `id`
- results table:
  - partition key: `userId`
  - sort key: `completedAtMillis`

## Frontend Flow

Primary file:

- `frontend/src/App.tsx`

The app is stateful and currently does not use a routing library.

Screens:

- `home`
- `setup`
- `quiz`
- `complete`
- `review`

### Frontend state model

Key local state includes:

- selected leagues
- difficulty
- current quiz id
- question list from `POST /v0/quiz`
- current question index
- current typed answer
- correct/wrong/skipped/hint counts for UI summary
- review payload fetched from `GET /v0/quiz/{quizId}`
- local per-question hint reveal state

### Frontend API base URL

In `frontend/src/App.tsx`:

- reads `VITE_API_BASE_URL`
- appends `/v0` in code

Current logic:

- local dev falls back to empty root, which becomes `/v0`
- Vite dev server proxies `/v0` to `http://localhost:8080`
- production should provide backend root only, without `/v0`

Production env file:

- `frontend/.env.production`

Current production backend root:

- `https://sports-quiz-test.ecnebyh1nvh4y.eu-west-2.cs.amazonlightsail.com`

### UX details already implemented

- home screen shows app logo from `frontend/src/assets/sports-quiz-logo.png`
- quiz input auto-focuses on question change
- easy mode gives two attempts
- wrong answers are shown in red
- skip and hint buttons exist
- review screen shows:
  - logo
  - submitted answer
  - hint used
  - correct answer
- completion screen shows counts and score

## Local Development

### Backend

Run from repo root:

```bash
./gradlew bootRun
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Vite serves the frontend on `http://localhost:5173` and proxies `/v0` to the backend on `http://localhost:8080`.

### DynamoDB Local

Make targets:

- `make db-up`
- `make db-down`
- `make db-logs`
- `make dev-backend`

`make dev-backend` currently starts DynamoDB Local and then runs `./gradlew bootRun`.

## Deployment Notes

### Backend container

Files:

- `Dockerfile`
- `scripts/build_docker.sh`
- `scripts/push_ecr.sh`

The Docker image is built for `linux/amd64`.

The Dockerfile currently sets:

- `SPRING_ACTIVE_PROFILES=prod`

Note: Spring's standard property is usually `SPRING_PROFILES_ACTIVE`. The current Dockerfile uses `SPRING_ACTIVE_PROFILES`. If profile activation is not behaving as expected, this is the first place to inspect.

### Frontend deploy

Script:

- `scripts/push_s3_frontend.sh`

Behavior:

- installs frontend deps
- builds the frontend
- optionally injects `VITE_API_BASE_URL`
- syncs `frontend/dist` to S3
- optionally invalidates CloudFront

CloudFront frontend origin currently expected:

- `https://d2323rgqey2wlv.cloudfront.net`

## Other Utility Code

Image tooling lives under:

- `image-generation/`

Notable files:

- `image-generation/remove-text-from-image.py`
- `image-generation/requirements.txt`
- `image-generation/setup_venv.sh`

Purpose:

- download or read a logo image
- detect text using OCR
- mask/inpaint the text region

This is auxiliary tooling, not part of the quiz runtime.

## Recommended First Files To Read

If a future agent needs to make changes, read these first:

1. `src/main/resources/application.yaml`
2. `src/main/kotlin/com/elliotmoose/Sports/Quiz/service/QuizService.kt`
3. `src/main/kotlin/com/elliotmoose/Sports/Quiz/api/SportsQuizController.kt`
4. `src/main/kotlin/com/elliotmoose/Sports/Quiz/repository/LocalQuizRepository.kt`
5. `src/main/kotlin/com/elliotmoose/Sports/Quiz/repository/LocalResultRepository.kt`
6. `frontend/src/App.tsx`
7. `README.md`

If working on Dynamo behavior, also read:

1. `src/main/kotlin/com/elliotmoose/Sports/Quiz/config/DynamoConfig.kt`
2. `src/main/kotlin/com/elliotmoose/Sports/Quiz/config/DynamoSeeder.kt`
3. `src/main/kotlin/com/elliotmoose/Sports/Quiz/repository/DynamoQuizRepository.kt`
4. `src/main/kotlin/com/elliotmoose/Sports/Quiz/repository/DynamoResultRepository.kt`

## Known Gaps / Things To Watch

These are the main areas where the current implementation is functional but not final:

- active quizzes are not persisted; only summarized results are persisted
- all users are currently collapsed into `single-user`
- the frontend keeps some UI summary counts locally instead of deriving everything from the review payload
- `quiz.settings.min-questions` is currently `1`, which differs from the original 10-25 requirement
- `DynamoConfig` always creates a Dynamo client even if both storages are local
- the Dockerfile uses `SPRING_ACTIVE_PROFILES` instead of standard `SPRING_PROFILES_ACTIVE`
- `AWS_SECRET_CODE` is likely a typo/nonstandard env var name and may need revisiting

## Safe Assumptions For Future Work

Unless the repo changes materially, a new agent can assume:

- questions should remain local JSON unless explicitly moved
- results may later move to Dynamo
- quiz sessions are still ephemeral and in-memory
- the frontend is intentionally simple and centralized in `App.tsx`
- the main HTTP contract is under `/v0/quiz`
- hints are embedded in quiz questions, not fetched separately
- face-mode data currently exists for NBA, NHL, MLB, and NFL only, and is not yet wired into the runtime quiz flow
