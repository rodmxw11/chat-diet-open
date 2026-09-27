# Plan: deploy chat-diet to a Google Compute Engine VM

Status: not yet executed.

## Context

chat-diet runs today as the `chat-diet-backend` container on a home Windows PC (`docker-compose.yml`,
bind mount `backend/data` → `/app/data`). It only runs while that PC is up and signed in. Goal: run the
same image on a small always-on Google Compute Engine VM.

**Where the SQLite data lives:** on the VM's persistent disk, at `~/chat-diet/backend/data/chat-diet.db`.
Compose mounts it into the container at `/app/data/chat-diet.db`, the same layout as on the PC. The disk
outlives container rebuilds and VM reboots. Two things back it up: the existing `BackupJob`, which writes
nightly zips to `data/backups` (14 kept), and a daily disk-snapshot schedule, which keeps copies
off the disk.

There is exactly one live instance, always. After cutover, the PC container stays stopped and is kept
only as a rollback.

Cloud Run was considered and rejected. Its filesystem is wiped on every restart, so SQLite would need
Litestream replication to Cloud Storage, which brings a window where recent writes can be lost, cold
starts, a nightly backup job that doesn't run while scaled to zero, and rework of the Tailscale/Alexa setup.

## Target setup

- **VM:** `e2-small` (2 GB RAM), Debian 12, zone `us-east1-b` (near Eastern time), 20 GB `pd-balanced`
  boot disk. Rough cost: about $18/month (VM ~$13, disk ~$2, external IPv4 ~$3.65, snapshots cents).
  `e2-micro` (free tier, 1 GB) could fit the ~512 MB JVM, but Docker + Tailscale leave little headroom.
- **Network:** no inbound firewall rules except SSH through Google's IAP proxy (`35.235.240.0/20`). The
  app is reached only over Tailscale, as on the PC. The VM keeps an external IP only so it can make
  outbound calls (Anthropic, USDA, Open Food Facts, Tailscale) without paying for Cloud NAT.
- **Tailscale machine name:** `chat-diet` → new URL `https://chat-diet.<tailnet>.ts.net:8443`.
- **Image contents:** the `eclipse-temurin:21-jre` base plus the boot jar only; there is no Node.js at runtime.
  Node is needed only on the machine that builds the jar: Gradle's `buildFrontend` task runs
  `npm run build` and copies the static bundle into the jar under `static/`.

## Steps

0. **Optional Dockerfile cleanup** (`backend/Dockerfile`):
   - Use `COPY --chown=appuser:appuser` for the jar instead of a later `chown -R /app`. The `chown`
     currently copies the ~97 MB jar into a second layer.
   - Change `EXPOSE 8080` to `EXPOSE 8443 8081`.
1. **Local tooling:** install the Google Cloud CLI, then `gcloud init`. Pick or create a project with
   billing enabled, and enable the Compute Engine API.
2. **Create the VM** with `gcloud compute instances create chat-diet ...` (machine type, image, disk as
   above).
   - Add a firewall rule allowing tcp:22 from the IAP range only.
   - Attach a snapshot schedule to the boot disk (`gcloud compute resource-policies create
     snapshot-schedule`, daily, 14-day retention).
3. **Provision the VM** (`gcloud compute ssh chat-diet --tunnel-through-iap`):
   - Install Docker Engine + the compose plugin, and add the user to the `docker` group.
   - Install Tailscale, run `tailscale up --hostname=chat-diet`, and approve the machine in the admin console.
   - `git clone` this repo to `~/chat-diet`. That supplies `docker-compose.yml` and `backend/Dockerfile`
     at the paths compose expects.
   - Run `tailscale cert` for `chat-diet.<tailnet>.ts.net` into `~/chat-diet/backend/certs/`.
   - Add a monthly cron job that re-runs `tailscale cert`, then `docker compose restart` (certs expire after 90 days).
4. **Ship the artifacts** (`gcloud compute scp --tunnel-through-iap`):
   - `backend/build/libs/backend-0.0.1-SNAPSHOT.jar` → `~/chat-diet/backend/build/libs/`
   - `application.yml` → `~/chat-diet/backend/src/main/resources/`, with the `server.ssl` cert paths
     edited to the new hostname.

   The image is built on the VM from this jar (`docker compose up -d --build`), so no Artifact Registry
   is needed. The jar contains no secrets, since `application.yml` is excluded from it.
5. **Cutover** (the only step that touches live data):
   1. On the phone, open the app and make sure the offline queue is empty. Queued entries belong to the old URL.
   2. On the PC: `docker compose down`. This also keeps the container from coming back after a reboot.
   3. Copy `backend/data/` (`chat-diet.db`, `backups/`, `OMRON-DOWNLOADS/`) to the VM. Leave the PC copy
      untouched for rollback.
   4. On the VM: `docker compose up -d --build`.
6. **Alexa:** follow `scripts/TAILSCALE-ALEXA-CONFIG.md` on the VM:
   - Turn on Funnel: 443 → `http://localhost:8081`. The node needs the `funnel` attribute in the tailnet policy.
   - Turn Funnel off on the PC.
   - Change the skill endpoint in the Alexa developer console to `https://chat-diet.<tailnet>.ts.net/alexa`.
7. **Phone:** remove the old PWA and install it from the new URL.
8. **Follow-ups once it works:**
   - Add an "Option C - Google Cloud VM" section to `HOW-TO-RUN.md` covering steps 2-7.
   - No code changes are needed. `docker-compose.yml` already sets `TZ: America/New_York`, mounts
     config, certs and data, and binds the Alexa port to loopback.

## Deploying later changes

Locally, run `backend\gradlew -p backend bootJar`. Then copy the jar to the VM and run `docker compose up -d --build` there.
For a config-only change, edit `application.yml` on the VM and run `docker compose restart`.

## Verification

- On the VM, `docker logs chat-diet-backend` shows "Tomcat started on ports 8443 (https), 8081 (http)",
  and log timestamps carry a `-04:00`/`-05:00` offset (Eastern time).
- From the tailnet, `curl https://chat-diet.<tailnet>.ts.net:8443/api/ping` returns `ok`, and
  `/api/dashboard/weight-trend` returns the same latest weigh-in as before cutover.
- From outside the tailnet, nothing answers on 8443 (check the public IP with `curl --max-time 5`).
- Log one test entry from the phone, check the row in the VM's DB with `sqlite3`, then delete it.
- Alexa: an utterance logs successfully, and `/alexa` is reachable only via Funnel.
- The next day, a new zip is in `data/backups` and `gcloud compute snapshots list` shows a snapshot.

## Rollback

Stop the VM container, then run `docker compose up -d` on the PC against its untouched `backend/data`.
First copy the VM's `chat-diet.db` back with `gcloud compute scp` if any entries were made there since cutover.
