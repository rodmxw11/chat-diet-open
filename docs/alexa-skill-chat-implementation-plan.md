# Implement the Alexa voice-logging skill for chat-diet

## Context

`docs/alexa-skill-chat-diet-spec.md` (recently rewritten from an earlier
Claude.ai-authored draft, superseded per `SPEC.md` §1/§11) lays out a plan to
let the user log food/weight/notes hands-free via an already-built Alexa
skill ("my food diary"). The Alexa console side is done; nothing on the Java
side exists yet. The doc is detailed and mostly correct, but it was written
without live access to the current codebase and contains several stale
references verified against the actual code. This plan corrects those and
sequences the implementation.

### Corrections to the doc (apply these before/while implementing — the doc's intent is right, its code pointers are wrong in five places)

1. **No `PromptAssembler.assemble()` method exists.** `ChatService` builds
   *one* `ChatClient` field at construction (`ChatService.java` ~line 59
   field, ~66-77 constructor) via `promptAssembler.tools()` passed to
   `.defaultTools(...)`. The system prompt is separately rebuilt fresh on
   *every* call inside `reply()` via `promptAssembler.systemPrompt()`
   (no-arg, lines 63-72 of `PromptAssembler.java`). There is no combined
   `assemble(boolean)` — the doc's B4.2 needs to become: add an
   exclusion-aware overload to **both** `tools(Set<String> excludedIntents)`
   and `systemPrompt(Set<String> excludedIntents)`, each with a no-arg
   overload delegating to `Set.of()`, filtering `IntentRegistry.enabledIntents()`
   by name before building fragments/tool names.
2. **`ChatRequest` is `record ChatRequest(String text, String clientSentAt)`**
   — two fields today, not the single-field doc reference. Add `String
   channel` as a third, nullable field.
3. **`reply()` has two existing overloads**, both returning `String`:
   `reply(String userText)` and `reply(LocalDate metabolicDate, LocalDateTime
   occurredAt, String userText)`. Threading `voiceChannel` through means
   adding it to the full-parameter overload and having the shorter overloads
   delegate `false`, per the doc's intent — just against the real signatures.
4. **There is no multipart/photo endpoint on `ChatController`.** It only has
   `POST /api/chat` (JSON body, `ChatRequest`) and `GET /api/chat/history`.
   Drop the doc's "multipart photo endpoint is untouched" line — it refers to
   something that doesn't exist.
5. **The cache-substring-matcher risk (doc §C) is half-fixed already.**
   `FoodItemRepository.findBestMatchByName` (lines 27-45) already rejects
   over-broad matches via a word-count-difference guard (committed earlier
   this session, `FoodItemRepository.java`) — "chicken" no longer falsely
   matches a cached "chicken salad sandwich". **`FdcClient.isPlausibleMatch`
   (lines 93-98) is still the naive, unguarded bidirectional-substring test**
   — disambiguation there is only a `qualifierCount`-based ranking (prefers
   the plainer of several plausible descriptions), not a rejection of
   over-broad matches. This remains a real risk specifically for voice
   (ASR strips qualifiers → short queries → more false substring hits against
   FDC's live search results, which then get cached and inherit an "FDC"
   provenance label). Recommendation: apply the same word-count-guard
   pattern to `isPlausibleMatch` as part of Build Order step 1, rather than
   treating it as fully out of scope — it's a small, consistent fix already
   proven safe on the `FoodItem` side.

Everything else in the doc — the interaction model (§A), the port/connector
design (§B1), signature verification approach (§B2), request handling table
(§B3), latency mitigations (§B5), response shaping (§B6), and the build
order (§D) — checks out against the current code and `SPEC.md`, and should
be followed as written.

## Implementation steps (following the doc's Build Order, §D, with the corrections above folded in)

**0. Update `docs/alexa-skill-chat-diet-spec.md`** with the five corrections
above (exact file/line references, the two-method `tools()`/`systemPrompt()`
split instead of `assemble()`, the real `ChatRequest`/`reply()` signatures,
removing the photo-endpoint line, and the FdcClient matcher status).

**1. Harden `FdcClient.isPlausibleMatch`** (`backend/src/main/java/com/chatdiet/fdc/FdcClient.java`)
with the same word-count-difference guard used in
`FoodItemRepository.findBestMatchByName` — reject a candidate description
whose word count exceeds the query's by more than a small threshold. Add a
unit test in `FdcClientTest.java` mirroring the existing plausibility tests,
covering a case that would have false-matched before (e.g. a short query
against a long multi-qualifier FDC description).

**2. Drain offline queues on every device** (manual step, not code) —
confirm zero pending writes in the PWA's queue panel on phone/tablet/desktop
before the port changes, since IndexedDB is origin-scoped and includes the
port.

**3. Move the app to port 8443** (`backend/src/main/resources/application.yml`
and `.yml.example`): change `server.port` from `443` to `8443`, keeping the
existing SSL cert config as-is. Update Docker port publishing. Verify
`https://<host>.<tailnet>.ts.net:8443/` from a tailnet device, then
re-install the PWA on each device (service worker re-registers at the new
origin).

**4. Add the second connector + path-isolation filter:**
   - New `backend/src/main/java/com/chatdiet/alexa/AlexaConnectorConfig.java`
     — a `WebServerFactoryCustomizer<TomcatServletWebServerFactory>` adding a
     second HTTP connector bound to `127.0.0.1` on a configurable port
     (`chat-diet.alexa.port`, default `8081`), following the existing nested
     `chat-diet.*` property convention seen in `application.yml`.
   - A filter (can live in the same `alexa` package) rejecting any
     non-`/alexa` path on the 8081 connector, and rejecting `/alexa` on 8443
     — defense in depth alongside Funnel's own `--set-path` restriction.
   - New `AlexaController.java` with a stub handler returning a hardcoded
     Alexa response envelope — no `ChatService`, no DB yet. Verify with a
     local `curl` against `127.0.0.1:8081/alexa`.
   - Publish the Alexa port in Docker as `127.0.0.1:8081:8081` (never
     `0.0.0.0`).

**5. Add signature verification** (`AlexaSignatureFilter.java` in the same
package), using `ask-sdk-servlet-support`'s `SkillRequestSignatureVerifier`
and `SkillRequestTimestampVerifier` (add `com.amazon.ask:ask-sdk-core` and
`com.amazon.ask:ask-sdk-servlet-support` to `build.gradle` — verify current
version before pinning; confirmed no such dependency exists today).
Read the raw request body once (e.g. via a request wrapper) before any
parsing, since the signature is computed over raw bytes. Cache the
downloaded Amazon certificate. Any verification failure → 400, before
touching anything else. Confirm the stub from step 4 now returns 400
without valid Alexa headers.

**6. Enable Tailscale Funnel** per `scripts/TAILSCALE-ALEXA-CONFIG.md` §6
(`tailscale funnel --https=443 --set-path=/alexa http://localhost:8081`).
Verify from **off the tailnet** (cellular, not home wifi) — a 400 from the
signature filter is the correct, expected result and proves both routing and
verification are live.

**7. Point the ASK console endpoint** at the Funnel URL and test the stub
from the Alexa simulator.

**8. Wire the three intents to `ChatService`:**
   - Add `String channel` to `ChatRequest`.
   - Add exclusion-aware overloads to `PromptAssembler.tools(Set<String>)`
     and `.systemPrompt(Set<String>)` (no-arg overloads delegate to
     `Set.of()`), filtering by intent name (`show_chart`, `run_sql` per
     `intents.yaml` lines 139/158) before building the tool list / prompt
     fragments, and appending a voice-specific instruction (no screen, speak
     full numbers, 1-3 sentences) when excluding.
   - In `ChatService`, build two `ChatClient` fields at construction —
     `webChatClient` (existing behavior) and `voiceChatClient` (built from
     `tools(VOICE_EXCLUDED_INTENTS)`). **Verify at implementation time**
     whether the injected `ChatClient.Builder` can safely produce two
     independent clients from two `.defaultTools(...)` calls, or whether a
     fresh builder instance is needed per client — Spring AI's builder
     mutation semantics should be checked directly rather than assumed.
   - Add `boolean voiceChannel` to the full `reply(...)` overload; existing
     shorter overloads delegate `false`. When true, use `voiceChatClient` and
     `systemPrompt(VOICE_EXCLUDED_INTENTS)`.
   - `AlexaController` builds the `AteIntent`/`NoteIntent`/`WeightIntent`
     carrier-phrase strings exactly as `MessageInput.tsx` does
     (`"I ate " + text`, `"Note that " + text`, `"Weight " + text` — confirmed
     matching prefill strings), calls `ChatService.reply(..., voiceChannel=true)`,
     and handles the remaining request types per the doc's §B3 table
     (`LaunchRequest`, `AMAZON.HelpIntent`, `AMAZON.FallbackIntent`,
     `AMAZON.StopIntent`/`CancelIntent`, `SessionEndedRequest`), setting
     `shouldEndSession` and a `reprompt` as specified.

**9. Templated confirmations + response shaping (§B5/B6):** when a log tool
returns `Success`, template the spoken confirmation directly from the tool
payload instead of a second Haiku round trip (`"Logged. %d calories."`).
Tighten the FDC timeout on the voice path (~1.5s) with fallback to model
estimate. Add SSML `<say-as interpret-as="cardinal">` around calorie/weight
numbers. Keep replies under ~15 words. Defensively strip any
chart/table-shaped payload in the controller even though voice excludes
those intents.

## Verification

- `cd backend && ./gradlew compileJava compileTestJava test` after each
  numbered step that touches Java, not just at the end.
- Step 1: new `FdcClientTest` case proving the guard rejects a previously
  false-positive match.
- Step 4: `curl` against `127.0.0.1:8081/alexa` locally (stub envelope).
- Step 5: same `curl`, now expecting 400 without valid signature headers.
- Step 6: `curl -i https://<host>.<tailnet>.ts.net/alexa` from a phone on
  cellular data (not tailnet wifi) — expect 400, not a timeout or 404.
- Step 7: Alexa developer console simulator — confirm intent/slot routing
  via the JSON Input panel.
- Step 8-9: real Echo device end-to-end per the doc's §E — one-shot log,
  a multi-turn session (`open my food diary` → weigh-in → food → `stop`),
  cross-channel check (log via Alexa, confirm the same metabolic day's Chat
  History in the PWA), a fallback utterance (expect "Sorry?", nothing
  written), and a post-midnight-pre-rollover log (confirm it files under the
  prior metabolic day per `DayBoundaryService`).
- Throughout: never touch the real `chat-diet.db` interactively — use the
  established scratch-copy + throwaway `bootRun` workflow for anything short
  of the final real-device verification in steps 6+.

## Critical files

- `docs/alexa-skill-chat-diet-spec.md` (corrections, step 0)
- `backend/src/main/java/com/chatdiet/fdc/FdcClient.java` (step 1)
- `backend/src/test/java/com/chatdiet/fdc/FdcClientTest.java` (step 1)
- `backend/src/main/resources/application.yml`, `.yml.example` (steps 3-4)
- `backend/src/main/java/com/chatdiet/alexa/AlexaConnectorConfig.java` (new, step 4)
- `backend/src/main/java/com/chatdiet/alexa/AlexaController.java` (new, steps 4, 8-9)
- `backend/src/main/java/com/chatdiet/alexa/AlexaSignatureFilter.java` (new, step 5)
- `backend/build.gradle` (step 5, ASK SDK deps)
- `backend/src/main/java/com/chatdiet/chat/ChatRequest.java` (step 8)
- `backend/src/main/java/com/chatdiet/chat/ChatService.java` (step 8)
- `backend/src/main/java/com/chatdiet/intent/PromptAssembler.java` (step 8)
- `scripts/TAILSCALE-ALEXA-CONFIG.md` (steps 3, 6 — network/cert steps, already written)

No frontend files change.