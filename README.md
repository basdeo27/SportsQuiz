# Sports Quiz App

The quiz is live at [funsportsquiz.com](https://funsportsquiz.com).

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

### Frontend API base URL

By default the frontend uses `/v0` (works with the dev proxy). For production builds, set:

```bash
VITE_API_BASE_URL=https://your-api-domain npm run build
```

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

## ECR push script

```bash
./scripts/push_ecr.sh <aws_account_id> <aws_region> <ecr_repo_name> [image_tag]
```

## S3 frontend push script

```bash
./scripts/push_s3_frontend.sh <aws_region> <s3_bucket> <cloudfront_distribution_id> [api_base_url]
```


We got our images from espn: https://site.api.espn.com/apis/site/v2/sports/hockey/nhl/teams
