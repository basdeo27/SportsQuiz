# Sports Quiz App

## Backend (Spring Boot)

From the repo root:

```bash
./gradlew bootRun
```

The API will be available at `http://localhost:8080`.

## Frontend (React + Vite)

From the repo root:

```bash
cd frontend
npm install
npm run dev
```

Vite runs at `http://localhost:5173` and proxies `/v0` requests to the backend at `http://localhost:8080`.

## Local NoSQL (DynamoDB Local)

Requires Docker. From the repo root:

```bash
make db-up
```

Stop it with:

```bash
make db-down
```

To use DynamoDB storage, set the per-repository storage flags:

```yaml
quiz:
  questions:
    storage: local
  results:
    storage: dynamo
  dynamo:
    seed: true
```

## Docker

Build the image:

```bash
docker build -t sports-quiz-backend .
```

Run locally:

```bash
docker run --rm -p 8080:8080 sports-quiz-backend
```

## ECR push script

```bash
./scripts/push_ecr.sh <aws_account_id> <aws_region> <ecr_repo_name> [image_tag]
```


We got our images from espn: https://site.api.espn.com/apis/site/v2/sports/hockey/nhl/teams
