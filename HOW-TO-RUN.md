# How to Run chat-diet

## Prerequisites

- Java 21 (only needed if running the backend outside Docker)
- Node.js (for the frontend)
- Docker (only needed for the Docker option)
- An Anthropic API key

## One-time setup

The backend needs `backend/src/main/resources/application.yml`, which is gitignored because it holds personal biometrics. If it doesn't exist yet:

```
copy backend\src\main\resources\application.yml.example backend\src\main\resources\application.yml
```

Then edit it and fill in your `sex`, `birth-date`, `height-in`, `goal.weekly-rate-lbs`, and `spring.ai.anthropic.api-key` (your Anthropic API key, as a literal value). The file is gitignored, so it's safe to put the real key there directly - no environment variable needed.

## Option A - Local dev (fastest for iterating)

**Backend** (Spring Boot, port 8080):

```
cd backend
gradlew bootRun
```

This creates a SQLite file database at `backend/data/chat-diet.db` on first run and applies Liquibase migrations automatically.

**Frontend** (Vite dev server):

```
cd frontend
npm install
npm run dev
```

Open the URL Vite prints (typically `http://localhost:5173`).

## Option B - Docker (backend only)

The Docker image only packages the backend. Build the jar and image with the Gradle task, then deploy:

```
cd backend
gradlew dockerBuild
```

```
cd ..
docker-deploy.cmd
```

This builds/rebuilds the `chat-diet-backend` image, stops any previous container, and starts a new one on `http://localhost:8080`, with `backend\data` mounted into the container so the SQLite database and backups persist across restarts. The API key is already baked into the image via `application.yml` (which Gradle packages into the jar), so no environment variable needs to be passed at `docker run` time.

The frontend is not containerized. Run it separately with `npm run dev`, or build a static bundle with `npm run build` (output in `frontend/dist`) and serve it with any static file server.

## Verifying it's up

- Backend: `http://localhost:8080` should respond (check `/api/...` endpoints or backend logs for "Started BackendApplication").
- Frontend: open the Vite dev URL and confirm the chat UI loads and can reach the backend.

## API documentation

Once the backend is running (either option above), the REST API is documented live via springdoc-openapi:

- **Swagger UI** (interactive, browsable): `http://localhost:8080/swagger-ui/index.html`
- **Raw OpenAPI spec** (JSON): `http://localhost:8080/v3/api-docs`

This is generated straight from the controller code, so it always reflects the current endpoints.

## Troubleshooting

- **Backend fails to start, or the model calls fail with an auth error** - check `spring.ai.anthropic.api-key` in `backend/src/main/resources/application.yml` is set to a real key, not the placeholder.
- **Backend fails to start with a missing `application.yml`** - see One-time setup above.
- **Docker build fails to find the jar** - run `gradlew dockerBuild` (not a bare `docker build`); it depends on `bootJar` so the jar always gets built first.
