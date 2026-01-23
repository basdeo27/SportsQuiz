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

To use DynamoDB instead of the local JSON repository, set:

```yaml
quiz:
  dynamo:
    enabled: true
    seed: true
```


We got our images from espn: https://site.api.espn.com/apis/site/v2/sports/hockey/nhl/teams
