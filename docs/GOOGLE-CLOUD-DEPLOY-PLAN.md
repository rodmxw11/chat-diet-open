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

- **VM:** `e2-micro` (1 GB RAM, ¼ vCPU sustained with bursts), Debian 12, zone `us-east1-b` (a free-tier
  region near Eastern time), 30 GB `pd-standard` boot disk (the free tier covers standard disks, not
  balanced), plus a 2 GB swap file. Rough cost: $0-4/month. The VM and disk fall under the free tier,
  snapshots cost cents, and the external IPv4 (~$3.65) may be billed; confirm against the current GCP
  free-tier terms before relying on $0. If it struggles, resize to `e2-small` (see Resizing).
- **JVM limits:** `JAVA_TOOL_OPTIONS=-Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC`, set in the VM's
  `.env`. Without them, the JVM sizes its heap from host RAM.
- **Memory test (2026-09-27)**, local, before choosing e2-micro: the container ran with a hard 600 MB limit
  and no swap (about what's left after Debian + Docker + Tailscale on a 1 GB VM), 0.5 CPU, the JVM limits
  above, and the demo database.
  - Startup took 37 s, including Liquibase, and used 302 MB.
  - Load: 80 dashboard requests, 3 `/api/export` backup zips, chat history, and one real `/api/chat`
    turn through Spring AI and its tools.
  - Peak memory was 342 MB, with no out-of-memory kill and no restarts. The chat turn took 6.6 s,
    mostly waiting on the Anthropic API.
- **Network:** no inbound firewall rules except SSH through Google's IAP proxy (`35.235.240.0/20`). The
  app is reached only over Tailscale, as on the PC. The VM keeps an external IP only so it can make
  outbound calls (Anthropic, USDA, Open Food Facts, Tailscale) without paying for Cloud NAT.
- **Tailscale machine name:** `chat-diet` → new URL `https://chat-diet.<tailnet>.ts.net:8443`.
- **Image contents:** the `eclipse-temurin:21-jre` base plus the boot jar only; there is no Node.js at runtime.
  Node is needed only on the machine that builds the jar: Gradle's `buildFrontend` task runs
  `npm run build` and copies the static bundle into the jar under `static/`.
- **Image registry:** a private Docker repository in Google Artifact Registry, `us-east1`, next to the
  VM (`us-east1-docker.pkg.dev/<project>/chat-diet/backend`).
  - The PC builds and pushes the images. The VM only pulls, and never builds.
  - Each image is tagged with its git short SHA, so rolling back means switching to an older tag.
  - The VM authenticates with its own service account, so no registry password is stored on it.
  - Pulls within the same region are free. Storage beyond the free 0.5 GB is $0.10/GB-month, and a
    cleanup policy keeps it small.
  - The image holds no secrets, because `application.yml` is excluded from the jar.
  - The VM must stay an x86 (`e2`) machine: the PC builds amd64 images, which an ARM `t2a` VM can't run.

## Steps

0. **Repo changes** (commit these before deploying):
   - `backend/Dockerfile`: use `COPY --chown=appuser:appuser` for the jar instead of a later `chown -R /app`.
     The `chown` currently copies the ~97 MB jar into a second layer, which every push would upload
     again. Also change `EXPOSE 8080` to `EXPOSE 8443 8081`.
   - `docker-compose.yml`: change `image:` to `${CHAT_DIET_IMAGE:-chat-diet-backend}:${TAG:-latest}`
     and keep `build: ./backend`. With no `.env`, local behavior is unchanged. On a machine that deploys,
     `.env` (already gitignored) sets `CHAT_DIET_IMAGE=us-east1-docker.pkg.dev/<project>/chat-diet/backend`
     and `TAG=<git short SHA>`. This keeps the project ID out of the public repo.
   - `docker-compose.yml`: add `JAVA_TOOL_OPTIONS: ${JAVA_TOOL_OPTIONS:-}` under `environment`. The VM's
     `.env` sets it to the tested flags (see JVM limits above). The PC leaves it unset and is unaffected.
1. **Local tooling:** install the Google Cloud CLI, then `gcloud init`. Pick or create a project with
   billing enabled, and enable the Compute Engine and Artifact Registry APIs.
2. **Create the registry and the VM:**
   - `gcloud artifacts repositories create chat-diet --repository-format=docker --location=us-east1`,
     with a cleanup policy that keeps the 10 most recent versions.
   - On the PC, run `gcloud auth configure-docker us-east1-docker.pkg.dev` once.
   - Create a service account `chat-diet-vm`, and grant it `roles/artifactregistry.reader` on the
     `chat-diet` repository only.
   - Create the VM with `gcloud compute instances create chat-diet ...` (machine type, image and disk as
     above), passing `--service-account=chat-diet-vm@<project>.iam.gserviceaccount.com --scopes=cloud-platform`.
     The IAM role, not the scope, limits what the VM can do.
   - Add a firewall rule allowing tcp:22 from the IAP range only.
   - Attach a snapshot schedule to the boot disk (`gcloud compute resource-policies create
     snapshot-schedule`, daily, 14-day retention).
3. **Provision the VM** (`gcloud compute ssh chat-diet --tunnel-through-iap`):
   - Create a 2 GB swap file (`fallocate -l 2G /swapfile`, `chmod 600`, `mkswap`, `swapon`, add it to
     `/etc/fstab`) and set `vm.swappiness=10` in `/etc/sysctl.d/`. It's a safety net for spikes, not
     working memory.
   - Install Docker Engine + the compose plugin, and add the user to the `docker` group.
   - Install Tailscale, run `tailscale up --hostname=chat-diet`, and approve the machine in the admin console.
   - `git clone` this repo to `~/chat-diet` to get `docker-compose.yml`, which `git pull` keeps current.
     The VM doesn't build, so it needs no jar.
   - Run `gcloud auth configure-docker us-east1-docker.pkg.dev`. Debian images on GCE ship with `gcloud`,
     and this makes `docker pull` use the VM's service account.
   - Run `tailscale cert` for `chat-diet.<tailnet>.ts.net` into `~/chat-diet/backend/certs/`.
   - Add a monthly cron job that re-runs `tailscale cert`, then `docker compose restart` (certs expire after 90 days).
4. **Build, push, and ship the config:**
   - On the PC: run `backend\gradlew -p backend bootJar`. Set `CHAT_DIET_IMAGE` and `TAG` (the git short
     SHA) in `.env`, then run `docker compose build` and `docker compose push`.
   - `gcloud compute scp --tunnel-through-iap` `application.yml` → `~/chat-diet/backend/src/main/resources/`,
     with the `server.ssl` cert paths edited to the new hostname.
   - On the VM: write `~/chat-diet/.env` with the same `CHAT_DIET_IMAGE` and `TAG`, plus `JAVA_TOOL_OPTIONS`,
     then run `docker compose pull`.
5. **Cutover** (the only step that touches live data):
   1. On the phone, open the app and make sure the offline queue is empty. Queued entries belong to the old URL.
   2. On the PC: `docker compose down`. This also keeps the container from coming back after a reboot.
   3. Copy `backend/data/` (`chat-diet.db`, `backups/`, `OMRON-DOWNLOADS/`) to the VM. Leave the PC copy
      untouched for rollback.
   4. On the VM: `docker compose up -d`. Never pass `--build` on the VM.
6. **Alexa:** follow `scripts/TAILSCALE-ALEXA-CONFIG.md` on the VM:
   - Turn on Funnel: 443 → `http://localhost:8081`. The node needs the `funnel` attribute in the tailnet policy.
   - Turn Funnel off on the PC.
   - Change the skill endpoint in the Alexa developer console to `https://chat-diet.<tailnet>.ts.net/alexa`.
7. **Phone:** remove the old PWA and install it from the new URL.
8. **Follow-ups once it works:**
   - Add an "Option C - Google Cloud VM" section to `HOW-TO-RUN.md` covering steps 2-7.
   - Apart from step 0, no code changes are needed. `docker-compose.yml` already sets
     `TZ: America/New_York`, mounts config, certs and data, and binds the Alexa port to loopback.

## Deploying later changes

1. On the PC:
   - Run `backend\gradlew -p backend bootJar`.
   - Set `TAG` in `.env` to the new commit's short SHA.
   - Run `docker compose build`, then `docker compose push`.
2. On the VM: set `TAG` in `.env` to the same value, then run `docker compose pull` and `docker compose up -d`.

To roll back a bad release, set `TAG` on the VM to the previous SHA and run `docker compose up -d`.
For a config-only change, edit `application.yml` on the VM and run `docker compose restart`.

## Resizing

Resize if the container restarts after running out of memory (`docker inspect chat-diet-backend` →
`RestartCount`), swap use stays high, or chat replies get sluggish. The steps are
`gcloud compute instances stop chat-diet`, then `gcloud compute instances set-machine-type chat-diet
--machine-type=e2-small`, then `gcloud compute instances start chat-diet`. That's a couple of minutes of
downtime, with the disk and data untouched. On e2-small the JVM limits can be raised or dropped.

## Verification

- On the VM, `docker logs chat-diet-backend` shows "Tomcat started on ports 8443 (https), 8081 (http)",
  and log timestamps carry a `-04:00`/`-05:00` offset (Eastern time).
- From the tailnet, `curl https://chat-diet.<tailnet>.ts.net:8443/api/ping` returns `ok`, and
  `/api/dashboard/weight-trend` returns the same latest weigh-in as before cutover.
- From outside the tailnet, nothing answers on 8443 (check the public IP with `curl --max-time 5`).
- Log one test entry from the phone, check the row in the VM's DB with `sqlite3`, then delete it.
- Alexa: an utterance logs successfully, and `/alexa` is reachable only via Funnel.
- The next day, a new zip is in `data/backups` and `gcloud compute snapshots list` shows a snapshot.
- A week after cutover, `RestartCount` is still 0 and `free -m` shows little swap in use.

## Rollback

A bad release only needs the image tag switched back (see above). To abandon the VM entirely, stop
the VM container, then run `docker compose up -d` on the PC against its untouched `backend/data`.
First copy the VM's `chat-diet.db` back with `gcloud compute scp` if any entries were made there since cutover.
