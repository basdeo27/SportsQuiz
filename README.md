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
