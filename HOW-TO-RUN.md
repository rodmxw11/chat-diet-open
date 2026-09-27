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

## Option C - Google Cloud VM (always on)

The same Compose setup, running on a small Google Compute Engine VM, so the app stays up whether or not the home PC is. The PC builds and pushes images to a private Artifact Registry repository, and the VM only pulls them. The full one-time setup (project, billing, registry, firewall, VM, swap, Tailscale, certs, and moving the data over) is in [docs/GOOGLE-CLOUD-DEPLOY-PLAN.md](docs/GOOGLE-CLOUD-DEPLOY-PLAN.md).

**Layout:**
- **VM:** an `e2-micro` (1 GB RAM plus a 2 GB swap file) running Debian 12. It's reachable only over Tailscale at `https://chat-diet.<tailnet>.ts.net:8443`. The public IP has no open ports, and SSH goes through Google's IAP tunnel.
- **Data:** the database lives on the VM's persistent disk at `~/chat-diet/backend/data/chat-diet.db`, mounted at `/app/data` exactly as in Option B. It's backed up by the app's nightly zips in `data/backups` and a daily disk snapshot kept 14 days.
- **Settings:** `~/chat-diet/.env` on the VM sets `CHAT_DIET_IMAGE`, `TAG` (the git short SHA to run), and `JAVA_TOOL_OPTIONS=-Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC`, which keeps the JVM within the e2-micro's memory.
- **Ownership:** the container runs as `appuser`, with UID/GID 10001. On Linux, the bind-mounted `backend/data`, `backend/certs`, and `application.yml` must be owned by `10001:10001`, or SQLite can't write and the TLS key can't be read. A root cron job renews the Tailscale cert monthly and re-applies that ownership.
- **Alexa:** the Tailscale Funnel for `/alexa` runs on the VM (see [scripts/TAILSCALE-ALEXA-CONFIG.md](scripts/TAILSCALE-ALEXA-CONFIG.md)).

**Deploying a change**, from the repo root in PowerShell, after committing:

```
backend\gradlew -p backend bootJar
$env:CHAT_DIET_IMAGE = "us-east1-docker.pkg.dev/<project>/chat-diet/backend"
$env:TAG = git rev-parse --short HEAD
docker compose build
docker compose push
```

Then on the VM (`gcloud compute ssh chat-diet --zone=us-east1-b --tunnel-through-iap`), set `TAG` in `~/chat-diet/.env` to the same SHA and run:

```
cd ~/chat-diet
docker compose pull
docker compose up -d
```

Set the variables only in the shell, not in a `.env` on the PC, so local Option B runs keep using the plain `chat-diet-backend` image. Never pass `--build` on the VM; it has no jar to build from.

**Day to day on the VM:**
- **Roll back a bad release:** set `TAG` back to the previous SHA, then run `docker compose up -d`. The registry keeps the 10 newest images.
- **Config change:** edit `backend/src/main/resources/application.yml` on the VM, then run `docker compose restart`. The file stays owned by `10001:10001` with mode `600`.
- **Logs and memory:** run `docker logs chat-diet-backend` and `docker stats --no-stream`. A `RestartCount` above 0 in `docker inspect chat-diet-backend` means the JVM was killed for running out of memory. If that happens, resize the VM to `e2-small` (stop, `gcloud compute instances set-machine-type`, start).

Only one instance may use the database at a time. Once the VM is live, the PC's `backend\data\chat-diet.db` is a stale copy, so never run Option A or B against it expecting current data.

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
