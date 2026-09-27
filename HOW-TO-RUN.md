# How to Run chat-diet

## Prerequisites

- Java 21 (also needed for the Docker option, to build the jar)
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

**Backend** (Spring Boot, HTTPS on port 8443, or HTTP on 8080 without the `server:` block):

```
cd backend
gradlew bootRun
```

This creates a SQLite file database at `backend/data/chat-diet.db` on first run and applies Liquibase migrations automatically.

That database starts empty, so the charts and history screens have nothing to show. To start against 60 days of generated data instead, copy the demo database into place and point the app at it:

```
copy demo\chat-diet-demo.db backend\data\
cd backend
gradlew bootRun --args="--spring.datasource.url=jdbc:sqlite:./data/chat-diet-demo.db"
```

Your own `chat-diet.db` is untouched - the two sit side by side, and you switch between them with that one argument. See [demo/README.md](demo/README.md).

**Frontend** (Vite dev server):

```
cd frontend
npm install
npm run dev
```

Open the URL Vite prints (typically `http://localhost:5173`).

## Option B - Docker (Compose)

The image runs the backend jar, which already bundles the built frontend, so the one container serves the whole PWA. Build the jar, then start it with Compose from the repo root:

```
backend\gradlew -p backend bootJar
docker compose up -d --build
```

`docker-compose.yml` publishes HTTPS on `8443` and the Alexa connector on `127.0.0.1:8081`. It sets `TZ` so metabolic-day boundaries match local time, and bind-mounts these host paths:

| Host | Container | Purpose |
|---|---|---|
| `backend\data` | `/app/data` | SQLite database (`chat-diet.db`), backups, OMRON imports |
| `backend\certs` | `/app/certs` (read-only) | Tailscale HTTPS cert/key |
| `backend\src\main\resources\application.yml` | `/app/config/application.yml` (read-only) | Config and API key |

The database is the same `backend\data\chat-diet.db` file that `bootRun` uses, so data persists across rebuilds and moves freely between the two options. **Never run both at once.** Stop `bootRun` before `docker compose up`, and vice versa, or two processes will write to one SQLite file.

`application.yml` is deliberately left out of the jar, so the API key is never baked into the image. After editing it, run `docker compose restart`; no rebuild is needed. Stop the container with `docker compose down`.

## Verifying it's up

- Backend: `https://<machine>.<tailnet>.ts.net:8443/api/ping` should return `ok` (or `http://localhost:8080/api/ping` if you omitted the `server:` HTTPS block), and the logs should show "Started BackendApplication".
- Frontend: open the Vite dev URL and confirm the chat UI loads and can reach the backend.

## API documentation

Once the backend is running (either option above), the REST API is documented live via springdoc-openapi:

- **Swagger UI** (interactive, browsable): `https://<machine>.<tailnet>.ts.net:8443/swagger-ui/index.html`
- **Raw OpenAPI spec** (JSON): `https://<machine>.<tailnet>.ts.net:8443/v3/api-docs`

Without the `server:` HTTPS block, use `http://localhost:8080` in place of the `https://...:8443` origin.

This is generated straight from the controller code, so it always reflects the current endpoints.

## Troubleshooting

- **Backend fails to start, or the model calls fail with an auth error** - check `spring.ai.anthropic.api-key` in `backend/src/main/resources/application.yml` is set to a real key, not the placeholder.
- **Backend fails to start with a missing `application.yml`** - see One-time setup above.
- **Docker build fails to find the jar** - run `backend\gradlew -p backend bootJar` before `docker compose up --build`; the image copies the prebuilt jar from `backend/build/libs`.
- **Container starts but has no config / API key errors** - it reads `application.yml` only from the mount, so check that `backend/src/main/resources/application.yml` exists.
