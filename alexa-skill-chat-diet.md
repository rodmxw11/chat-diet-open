# Alexa Skill for chat-diet

## Context

chat-diet is a personal, single-user diet/weight/vitals tracker whose entire interface is a chat box (`POST /api/chat`, backed by an LLM with ~27 tools). The backend currently only runs on a Tailscale tailnet (`https://<machine>.<tailnet>.ts.net`), has no application-level authentication at all, and its chat responses are already close to voice-ready (short, spoken-style sentences) except for two tools (`show_chart`, `run_sql`) whose whole design assumes a screen.

The goal: build an Alexa Skill so the same chat interface can be used hands-free ("Alexa, ask diet coach how many calories I have left"). Since Alexa's Lambda runs outside the tailnet, the backend needs to become internet-reachable (via `tailscale funnel`) and therefore needs its first real auth check. This plan covers both the backend changes required to expose it safely and the new Alexa Skill itself.

Decisions already made:
- Expose the backend via `tailscale funnel` (no new proxy infra).
- Lambda in Node.js using the official ASK SDK v2.
- Personal skill only — single Amazon dev account, dev-mode, no OAuth/account linking; Lambda holds one shared secret.
- Frontend fetch calls get updated in this same pass so the PWA doesn't break once auth is added.

---

## A. Backend changes (`backend/`)

### A1. Shared-secret filter

New file `backend/src/main/java/com/chatdiet/security/ApiKeyFilter.java` — a `@Component` implementing `OncePerRequestFilter` (already on the classpath via `spring-boot-starter-webmvc`; no new dependency, no `spring-boot-starter-security` needed for one header check).

- `shouldNotFilter`: skip everything except paths starting with `/api/`.
- Also skip `/api/sql-results/**` — its CSV download is a plain `<a href download>` in the frontend (`SqlResultTable.tsx:38`), which can't attach a custom header. `csvId` is a random `UUID` (`SqlResultStore.java:26`) from a short-lived, bounded in-memory cache, so leaving this one path unauthenticated is low-risk.
- Everything else under `/api/` (`/api/chat`, `/api/export`, `/api/barcode/decode`) requires the header.
- Compare `request.getHeader("X-Api-Key")` against `@Value("${chat-diet.api-key}")` using `MessageDigest.isEqual(...)` (constant-time). Mismatch/missing → `response.sendError(401)`, don't call the filter chain.

### A2. Config

- `backend/src/main/resources/application.yml` (untracked, confirmed via `git check-ignore`) — add under `chat-diet:`:
  ```yaml
  chat-diet:
    api-key: <openssl rand -hex 32>
  ```
- `backend/src/main/resources/application.yml.example` (tracked) — same key, placeholder value, comment matching the existing `api-key` comment style (see line 29-31 for the Anthropic key).

### A3. Voice channel (steer away from `show_chart`/`run_sql`)

`ChatService` currently builds **one** `ChatClient` at startup from `PromptAssembler.assemble()` (`ChatService.java:23-29`), so prompt/tools are fixed for the app's lifetime — there's no per-request override available. Minimal-diff fix: build two clients.

1. `ChatRequest` (`backend/src/main/java/com/chatdiet/chat/ChatRequest.java:13`) — add a 4th field: `String channel` (nullable; only `"voice"` changes behavior).
2. `PromptAssembler.assemble()` → `assemble(boolean voiceChannel)`. When `true`:
   - Exclude the `show_chart` and `run_sql` intents (by name) from `enabledIntents` before building `fragments`/`toolNames`.
   - Append a voice-specific instruction to the system prompt: no screen, speak full numbers aloud, never mention charts/tables, keep replies to 1-3 sentences.
   - Keep a no-arg `assemble()` delegating to `assemble(false)` so nothing else changes.
3. `ChatService` — build both `webChatClient` and `voiceChatClient` at construction; add a `boolean voiceChannel` parameter to `reply(LocalDate, LocalDateTime, String)`, with the existing overload delegating with `voiceChannel=false`.
4. `ChatController.chat(...)` (`ChatController.java:47-53`) — pass `"voice".equals(request.channel())` through to `chatService.reply(...)`. The multipart photo endpoint is unchanged (Alexa can't send photos).

### A4. Frontend: send the new header

- `frontend/src/store/chatSlice.ts:62` and `:102` — add `'X-Api-Key': <value>` to the `headers` of both `fetch('/api/chat', ...)` calls (the JSON one already has a `headers` object; the multipart one needs one added since it currently sends none).
- Value needs to reach the frontend build — bake it in at build time via an env var (e.g. Vite `VITE_API_KEY`) consumed the same way other frontend config is already injected (check existing pattern in the frontend build config before adding a new mechanism). Since this is a personal, same-origin PWA, a build-time constant is acceptable; just don't hardcode it in source — keep it out of git the same way `application.yml`'s secrets are kept out.
- `SqlResultTable.tsx:38` needs no change (A1 exempts that path).

### A5. Tailscale Funnel (operational)

- `server.ssl` on 443 is already configured with a real Tailscale cert (`application.yml:2-5`).
- Enable Funnel for the node (one-time, Tailscale admin console), then `tailscale funnel --bg 443`.
- Verify externally (e.g. over cellular, not the tailnet) before wiring up Alexa: `/api/chat` should 401 without `X-Api-Key` and respond normally with it.

---

## B. Alexa Skill (new `alexa-skill/` directory)

Standard `ask-cli` layout:
```
alexa-skill/
  ask-resources.json
  skill-package/skill.json
  skill-package/interactionModels/custom/en-US.json
  lambda/index.js
  lambda/chatClient.js
  lambda/package.json
```

### B1. Interaction model

- Invocation name: **"diet coach"**.
- One real intent, `CatchAllIntent`, with a single `AMAZON.SearchQuery` slot `{utteranceText}`. Sample utterance is just `{utteranceText}` — a `SearchQuery` slot can't be mixed with carrier phrases, so this is the whole interaction model's core: whatever is said after invocation gets forwarded to the backend nearly verbatim. This favors one-shot queries ("ask diet coach how many calories I have left") over natural multi-turn conversation, since Alexa still special-cases words like "stop"/"help"/"cancel" even with an open `SearchQuery` slot.
- Standard built-ins wired normally (not through the catch-all) so they route correctly: `AMAZON.HelpIntent`, `AMAZON.StopIntent`, `AMAZON.CancelIntent`, `AMAZON.FallbackIntent`.
- Skip a second carrier-phrase intent (e.g. `LogFoodIntent`) for v1 — added complexity without a clear win given `SearchQuery` already captures arbitrary text reasonably.

### B2. Session handling

- The skill sends **no session key at all**. The backend has no session concept any more: a turn is filed under the metabolic day it was composed on, so Alexa's turns land in the *same* conversation as the PWA's. That's a feature, not a collision — you can ask Alexa about the sandwich you photographed on your phone an hour earlier.
- `channel: "voice"` is a **prompt-style discriminator only** (which `ChatClient`/system prompt to use), never a history key. Don't let it grow into one.
- `shouldEndSession` heuristic: `true` unless the backend's `reply` ends in `?` (keep session open for a likely follow-up question from the model, e.g. an ambiguous `log_food` clarification).
- `SessionEndedRequestHandler`: no-op, just return the response — conversation state lives server-side.

### B3. Lambda handler structure (`lambda/index.js`, ASK SDK v2 `ask-sdk-core`)

- `LaunchRequestHandler` — short welcome, `shouldEndSession: false`.
- `CatchAllIntentHandler` — read `utteranceText` slot, call backend via `chatClient.js`, speak `reply`, apply the `shouldEndSession` heuristic.
- `HelpIntentHandler`, `CancelAndStopIntentHandler`, `FallbackIntentHandler` — canned responses.
- `SessionEndedRequestHandler` — no-op.
- `ErrorHandler` — catch backend timeout/5xx/network errors, speak a generic fallback instead of crashing.
- No request interceptor needed — nothing shared to set up per-request beyond what's local to each handler.

### B4. Backend client (`lambda/chatClient.js`)

- Node's built-in `https` module (no `axios`/`node-fetch` dependency — keeps the Lambda zip dependency-free) unless the pinned Lambda Node runtime is confirmed to ship global `fetch`, in which case that's fine too and simpler.
- POST `{ text, clientSentAt, channel: "voice" }` to `BACKEND_URL + "/api/chat"` with header `X-Api-Key: BACKEND_API_KEY`.
- Timeout well under Alexa's ~8s response budget (e.g. 7s) — note the Claude Haiku turnaround plus network must reliably fit in that window or the skill reads a generic Alexa timeout instead of the real reply.

### B5. Config/secrets

- `BACKEND_URL` and `BACKEND_API_KEY` as Lambda environment variables, set once via AWS Console (or `aws lambda update-function-configuration`) after the first `ask deploy` creates the function — not automated, one-time manual step for a single personal deployment.
- No secrets committed under `alexa-skill/`; gitignore any local `.env` used for `ask-cli` testing.

### B6. Deployment tooling

- `ask-cli` (`ask new` / `ask deploy`), self-hosted Lambda (not Alexa-hosted), since the Lambda needs outbound internet access to reach the Funnel URL. Keeps the interaction model as a versioned JSON file in this repo and deploys skill+Lambda together.

---

## Verification

1. Confirm the Funnel URL's `/api/chat` returns 401 without `X-Api-Key` and a normal reply with it, from outside the tailnet.
2. Confirm the PWA still works after the frontend header change (send a chat message, send a photo, download a SQL CSV).
3. `ask dialog` / `ask simulate --text "..."` for fast interaction-model + Lambda iteration without a device.
4. Alexa Developer Console "Test" tab (dev mode) — hear the actual TTS phrasing of numbers.
5. Real Echo device on your Amazon account (dev-mode skills are auto-available there, no publishing needed) — the real end-to-end test:
   - One-shot factual query ("how many calories do I have left") → full spoken sentence via `get_daily_target`.
   - A log action ("I ate a banana") → saves and echoes the number back.
   - A chart/SQL-shaped query ("show me my weight trend") → confirms voice-channel exclusion makes the model answer in words instead of "here you go."
   - Multi-turn continuity within one Alexa session (e.g. a fasting-status question followed by a pronoun-referencing follow-up) → confirms the day's history carries over correctly.
   - Cross-channel continuity: start a thread in the PWA, then continue it on Alexa → confirms both land in the same daily conversation.

### Critical files

- `backend/src/main/java/com/chatdiet/security/ApiKeyFilter.java` (new)
- `backend/src/main/java/com/chatdiet/chat/ChatService.java`
- `backend/src/main/java/com/chatdiet/chat/ChatRequest.java`
- `backend/src/main/java/com/chatdiet/chat/ChatController.java`
- `backend/src/main/java/com/chatdiet/intent/PromptAssembler.java`
- `backend/src/main/resources/application.yml.example`
- `frontend/src/store/chatSlice.ts`
- `alexa-skill/lambda/index.js`, `alexa-skill/lambda/chatClient.js` (new)
- `alexa-skill/skill-package/interactionModels/custom/en-US.json` (new)
