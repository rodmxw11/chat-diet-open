# Implement the Alexa voice-logging skill for chat-diet

## Context

`docs/alexa-skill-chat-diet-spec.md` (recently rewritten from an earlier
Claude.ai-authored draft, superseded per `SPEC.md` §1/§11) lays out a plan to
let the user log food/weight/notes hands-free via an already-built Alexa
skill ("my food diary"). The Alexa console side is done; nothing on the Java
side exists yet.

This plan has been reassessed once already, against a large merge
(`main`, commit `489b083`) that reworked food logging:
`FoodItemRepository`/`FdcClient`'s bidirectional-substring auto-matcher was
replaced with an exact `FOOD_ALIAS` table plus a `FoodResolver`
clarification protocol (see `docs/fixing-substring-problem-spec.md`). That
merge invalidated one build-order step outright and surfaced a new
transport-layer question — multi-turn clarification over voice — that
neither this doc nor the spec doc originally anticipated. The corrections
below fold in both the original code-pointer fixes and this second pass.

### Corrections to the doc (apply these before/while implementing)

1. **No `PromptAssembler.assemble()` method exists.** `ChatService` builds
   *one* `ChatClient` field at construction (`ChatService.java:59` field,
   `:66-77` constructor) via `promptAssembler.tools()` passed to
   `.defaultTools(...)`. The system prompt is separately rebuilt fresh on
   *every* call inside `reply()` via `promptAssembler.systemPrompt()`
   (no-arg, `PromptAssembler.java:63-72`). There is no combined
   `assemble(boolean)` — the doc's B4.2 needs to become: add an
   exclusion-aware overload to **both** `tools(Set<String> excludedIntents)`
   (currently no-arg, `PromptAssembler.java:79-85`) and
   `systemPrompt(Set<String> excludedIntents)`, each with a no-arg overload
   delegating to `Set.of()`, filtering `IntentRegistry.enabledIntents()` by
   name before building fragments/tool names.
2. **`ChatRequest` is `record ChatRequest(String text, String clientSentAt)`**
   — two fields today, not the single-field doc reference. Add `String
   channel` as a third, nullable field.
3. **`reply()` has two existing overloads**, both returning `String`:
   `reply(String userText)` (`ChatService.java:80-83`) and
   `reply(LocalDate metabolicDate, LocalDateTime occurredAt, String userText)`
   (`:92-135`). Threading `voiceChannel` through means adding it to the
   full-parameter overload and having the shorter overload delegate `false`.
4. **There is no multipart/photo endpoint on `ChatController`.** It only has
   `POST /api/chat` (JSON body, `ChatRequest`) and `GET /api/chat/history`.
   Drop the doc's "multipart photo endpoint is untouched" line — it refers
   to something that doesn't exist.
5. **`isPlausibleMatch` no longer exists at all — this replaces the earlier
   correction #5, which is itself now stale.** The bidirectional-substring
   matcher this doc's §C worried about (shared by `FoodItemRepository` and
   `FdcClient`) has been architecturally replaced, not patched.
   `FdcClient.search()` (`FdcClient.java:42`) now returns every ranked
   candidate unfiltered — no plausibility rejection at all, by design (its
   javadoc: "FDC's role here is recall, not automated selection").
   Disambiguation moved up into `FoodResolver`
   (`backend/src/main/java/com/chatdiet/food/resolve/FoodResolver.java`),
   which does an exact `FOOD_ALIAS` lookup first and falls back to a
   numbered clarification list on a miss — never a silent auto-select. This
   is a strictly stronger fix than any word-count-guard patch to
   `isPlausibleMatch` would have been, since that method is gone.
   **§C's "fix the matcher before wiring Alexa" framing is obsolete** —
   there is no matcher left to fix; see correction 6 for what replaces it as
   the open question.
6. **New: the `log_food` clarification protocol needs to work over voice —
   and it does, for free, with no new logging-side code.** Since the
   substring rework, `log_food` on an ambiguous or unknown food returns
   `ToolResult.NeedsClarification` with a numbered list, each option tagged
   with a hidden `[ref:item:N]`/`[ref:fdc:N]`/`[ref:estimate]` marker; the
   model is instructed (in the tool's own description and in
   `intents.yaml`) to relay only the human-readable options and echo the
   chosen marker back as `resolvedFoodItemId`/`resolvedFdcId`/`useEstimate`
   on the next call. A partial multi-item batch similarly echoes a group id
   ("(group #123)") for a follow-up to attach to via `attachToGroupId`.

   Verified in `ChatService.reply(LocalDate, LocalDateTime, String)`
   (`:92-135`): history is fetched as `historyStore.get(metabolicDate)` —
   **keyed purely by metabolic day, not by channel, session, or device**.
   An Alexa turn and a web turn on the same day share the exact same
   conversation the model sees. A clarification list emitted mid-Alexa-
   session stays in that day's history, and the very next turn — spoken or
   typed — carries full context; the model resolves "the honey wheat one"
   the same way it would on the web. **No code change is needed in
   `LogFoodTool`, `FoodResolver`, or `ChatService` for this to work.**

   Two things *do* need attention at the `AlexaController`/session layer,
   folded into Build Order step 8 below:
   - `ChatService.reply()` returns a bare `String` with no structural
     signal distinguishing "action completed" from "I need one more thing
     from you." Rather than sniffing reply text for a trailing "?"
     (fragile — SSML/number formatting makes this unreliable), set
     `shouldEndSession: false` with a short reprompt ("Anything else?")
     after **every** `AteIntent`/`NoteIntent`/`WeightIntent` turn,
     unconditionally — not just when the reply looks like a question. This
     matches the interaction model's existing session-lifecycle design and
     needs no new state.
   - The 3-intent design prepends a fixed carrier phrase per intent
     (`"I ate "`, `"Note that "`, `"Weight "`) to whatever `AMAZON.SearchQuery`
     captured. A short clarification answer like "the honey wheat one" has
     no strong signal steering Alexa's NLU to `AteIntent` specifically — it
     could land in any of the three, or Fallback, prepending the wrong verb
     framing (e.g. "Note that the honey wheat one"). The model, given the
     full conversation including its own just-asked clarifying question,
     will very likely still resolve this correctly regardless — **don't add
     carrier-phrase-stripping or intent-guessing logic preemptively.** Test
     it on a real Echo (§E) and only build a mitigation if it actually
     misbehaves.
7. **`intents.yaml` line references drift — re-verify before citing them.**
   `log_food`'s prompt fragment grew substantially in the alias rework
   (now ~45 lines). As of this pass: `show_chart` starts at line 141,
   `run_sql` at line 160 — both have moved before and will likely move
   again as the fragments are edited further. Don't trust either doc's line
   numbers without re-checking.

Everything else in the doc — the interaction model (§A), the port/connector
design (§B1), signature verification approach (§B2), request handling table
(§B3), latency mitigations (§B5), response shaping (§B6), and the build
order shape (§D) — checks out against the current code and `SPEC.md`, and
should be followed as written.

## Implementation steps (following the doc's Build Order, §D, with the corrections above folded in)

**0. Update `docs/alexa-skill-chat-diet-spec.md`** with the corrections
above (exact file/line references, the two-method `tools()`/`systemPrompt()`
split instead of `assemble()`, the real `ChatRequest`/`reply()` signatures,
removing the photo-endpoint line, replacing §C's matcher-fix framing with
the clarification-protocol-over-voice finding, and correcting the
`intents.yaml` line references).

**1. Drain offline queues on every device** (manual step, not code) —
confirm zero pending writes in the PWA's queue panel on phone/tablet/desktop
before the port changes, since IndexedDB is origin-scoped and includes the
port.

**2. Move the app to port 8443** (`backend/src/main/resources/application.yml`
and `.yml.example`): change `server.port` from `443` to `8443`, keeping the
existing SSL cert config as-is. Update Docker port publishing. Verify
`https://<host>.<tailnet>.ts.net:8443/` from a tailnet device, then
re-install the PWA on each device (service worker re-registers at the new
origin).

**3. Add the second connector + path-isolation filter:**
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

**4. Add signature verification** (`AlexaSignatureFilter.java` in the same
package), using `ask-sdk-servlet-support`'s `SkillRequestSignatureVerifier`
and `SkillRequestTimestampVerifier` (add `com.amazon.ask:ask-sdk-core` and
`com.amazon.ask:ask-sdk-servlet-support` to `build.gradle` — verify current
version before pinning; confirmed no such dependency exists today).
Read the raw request body once (e.g. via a request wrapper) before any
parsing, since the signature is computed over raw bytes. Cache the
downloaded Amazon certificate. Any verification failure → 400, before
touching anything else. Confirm the stub from step 3 now returns 400
without valid Alexa headers.

**5. Enable Tailscale Funnel** per `scripts/TAILSCALE-ALEXA-CONFIG.md`
(`tailscale funnel --https=443 --set-path=/alexa http://localhost:8081/alexa`
— the target must include `/alexa`, not just the bare host:port; `--set-path`
strips the mount-point prefix before forwarding, so a bare target makes the
backend see requests at `/` instead, which neither `AlexaController` nor
`AlexaPathIsolationFilter` recognize. Confirmed by testing.).
Verify from **off the tailnet** (cellular, not home wifi) — a 400 from the
signature filter is the correct, expected result and proves both routing and
verification are live.

**6. Point the ASK console endpoint** at the Funnel URL and test the stub
from the Alexa simulator.

**7. Wire the three intents to `ChatService`:**
   - Add `String channel` to `ChatRequest`.
   - Add exclusion-aware overloads to `PromptAssembler.tools(Set<String>)`
     and `.systemPrompt(Set<String>)` (no-arg overloads delegate to
     `Set.of()`), filtering by intent name (`show_chart`, `run_sql`) before
     building the tool list / prompt fragments, and appending a
     voice-specific instruction (no screen, speak full numbers, 1-3
     sentences) when excluding. **`log_food` is not excluded** — voice
     needs its full clarification-protocol instructions, per correction 6.
   - In `ChatService`, build two `ChatClient` fields at construction —
     `webChatClient` (existing behavior) and `voiceChatClient` (built from
     `tools(VOICE_EXCLUDED_INTENTS)`). **Verify at implementation time**
     whether the injected `ChatClient.Builder` can safely produce two
     independent clients from two `.defaultTools(...)` calls, or whether a
     fresh builder instance is needed per client — Spring AI's builder
     mutation semantics should be checked directly rather than assumed.
   - Add `boolean voiceChannel` to the full `reply(...)` overload; existing
     shorter overloads delegate `false`. When true, use `voiceChatClient`
     and `systemPrompt(VOICE_EXCLUDED_INTENTS)`. History lookup/append
     stays exactly as-is — `historyStore.get(metabolicDate)` needs no
     channel awareness.
   - `AlexaController` builds the `AteIntent`/`NoteIntent`/`WeightIntent`
     carrier-phrase strings exactly as `MessageInput.tsx` does
     (`"I ate " + text`, `"Note that " + text`, `"Weight " + text` — confirmed
     matching prefill strings), calls `ChatService.reply(..., voiceChannel=true)`,
     and handles the remaining request types per the doc's §B3 table
     (`LaunchRequest`, `AMAZON.HelpIntent`, `AMAZON.FallbackIntent`,
     `AMAZON.StopIntent`/`CancelIntent`, `SessionEndedRequest`). Set
     `shouldEndSession: false` with a reprompt for all three logging
     intents unconditionally, per correction 6.

**8. Templated confirmations + response shaping (§B5/B6):** when a log tool
returns `Success`, template the spoken confirmation directly from the tool
payload instead of a second Haiku round trip (`"Logged. %d calories."`) —
**verify at implementation time** whether Spring AI's tool-calling loop
always does a second model call to compose a final reply after a tool
result, since this determines whether the round-trip skip is achievable at
all for `Success`, or whether it's really just "keep the composed reply
short" (which the voice prompt instruction already asks for).
`NeedsClarification` results always need the full model-composed pass
regardless — there's no canned template for an open-ended clarification.
Tighten the FDC timeout on the voice path (~1.5s) with fallback to model
estimate. Add SSML `<say-as interpret-as="cardinal">` around calorie/weight
numbers. Keep replies under ~15 words. Defensively strip any
chart/table-shaped payload in the controller even though voice excludes
those intents.

## Verification

- `cd backend && ./gradlew compileJava compileTestJava test` after each
  numbered step that touches Java, not just at the end.
- Step 3: `curl` against `127.0.0.1:8081/alexa` locally (stub envelope).
- Step 4: same `curl`, now expecting 400 without valid signature headers.
- Step 5: `curl -i https://<host>.<tailnet>.ts.net/alexa` from a phone on
  cellular data (not tailnet wifi) — expect 400, not a timeout or 404.
- Step 6: Alexa developer console simulator — confirm intent/slot routing
  via the JSON Input panel.
- Steps 7-8: real Echo device end-to-end per the doc's §E — one-shot log,
  a multi-turn session (`open my food diary` → weigh-in → food → `stop`),
  cross-channel check (log via Alexa, confirm the same metabolic day's Chat
  History in the PWA), a fallback utterance (expect "Sorry?", nothing
  written), and a post-midnight-pre-rollover log (confirm it files under the
  prior metabolic day per `DayBoundaryService`).
- **New**: trigger a genuinely ambiguous food log via Alexa (e.g. two
  similarly-named cached items), confirm Alexa asks a spoken clarifying
  question with `shouldEndSession: false`, answer it in the same session,
  confirm it resolves to the right item and logs. Separately, note whether
  the answering utterance landed in `AteIntent` or a different intent
  (check the ASK console's request log) and whether resolution still
  succeeded regardless — only build a carrier-phrase mitigation if it
  actually fails.
- Throughout: never touch the real `chat-diet.db` interactively — use the
  established scratch-copy + throwaway `bootRun` workflow for anything short
  of the final real-device verification in steps 5+.

## Critical files

- `docs/alexa-skill-chat-diet-spec.md` (corrections, step 0)
- `backend/src/main/resources/application.yml`, `.yml.example` (steps 1-2)
- `backend/src/main/java/com/chatdiet/alexa/AlexaConnectorConfig.java` (new, step 3)
- `backend/src/main/java/com/chatdiet/alexa/AlexaController.java` (new, steps 3, 7-8)
- `backend/src/main/java/com/chatdiet/alexa/AlexaSignatureFilter.java` (new, step 4)
- `backend/build.gradle` (step 4, ASK SDK deps)
- `backend/src/main/java/com/chatdiet/chat/ChatRequest.java` (step 7)
- `backend/src/main/java/com/chatdiet/chat/ChatService.java` (step 7)
- `backend/src/main/java/com/chatdiet/intent/PromptAssembler.java` (step 7)
- `backend/src/main/resources/intents/intents.yaml` (step 7 — verify current line numbers before citing them anywhere)
- `scripts/TAILSCALE-ALEXA-CONFIG.md` (steps 2, 5 — network/cert steps, already written)

No frontend files change. No changes needed to
`backend/src/main/java/com/chatdiet/food/` (`LogFoodTool`, `FoodResolver`,
etc.) — the clarification protocol works over voice as-is.
