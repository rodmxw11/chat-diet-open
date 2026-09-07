# Seamless food entry: fewer questions, faster logging, editable aliases

*Review + implementation plan, 2026-09-07.*

## Context

Daily food entry is too chatty and too slow. Today the only path that logs without a clarifying question is an exact alias hit; the fuzzy matcher *never* auto-selects (even a sole 0.95 match becomes a numbered list), ambiguous picks are never remembered so the same question repeats forever, calories can't be used as a quantity anywhere, every entry pays an LLM round trip, and a barcode scan ends in a half-composed chat draft (`g <name>`) instead of a logged entry. The alias editor also has gaps: no aliases while creating an item, silent no-op on alias collisions, and item search ignores aliases. Approved direction: auto-log strong matches + learn from every pick, a deterministic LLM-bypassing fast path, and an inline quantity prompt after scans.

**Corrections found during review (change what we fix):**
- The 3 duplicate "greek yogurt" rows (ids 5, 9, 10) are `MODEL_ESTIMATE`, upc NULL — minted by `LogFoodTool.logEstimate` because `resolveItem` checks `useEstimate` *before* the alias lookup (`LogFoodTool.java:139`), not by the UPC path. `UpcResolutionService.upsertAndAlias`'s always-insert defect is real but has produced no duplicate UPCs yet.
- `correct_food_entry` never changes `foodItemId`, so a wrong auto-match's *identity* is fixed by delete-entry + delete-alias, not by correction. Auto-learned aliases get `source='AUTO'` so they're identifiable/deletable.
- Auto-accept and learn-from-ambiguous contradict `docs/fixing-substring-problem-spec.md` §4.2/§4.3 ("never auto-select", "Ambiguous never learns") — deliberate override; amend the spec + javadocs so docs don't silently diverge.
- Alexa constraint stands: chat clarifications stay plain-text numbered lists. The scan quantity prompt is PWA-only UI, which is fine.
- **User philosophy (2026-09-07)**: capture speed and consistency beat calorie precision — when in doubt, overestimate by a few percent; the metric that matters is long-term real weight loss. This endorses aggressive auto-accept (an imprecise log beats an interrupted one) and biases tie-breaks and model estimates high, never low.
- **Historical corpus check** (202 user messages in `chat_message`): 67 start "I ate/ate …", 8 "yesterday …", 10 are bare-number clarification replies, and exactly **1** is quantity-first — the scan prefill `g HIGH PROTEIN BAR…`, sent *without* grams typed (the draft UX failed in practice; user notes also ask for Sam's-Club-style scan-and-go, validating Phase F). Phase E's grammar is therefore corpus-informed (below), and routine typos ("tira masu", "bananna", "helmans") reinforce Phases B/C: learned aliases capture the user's actual spellings.

Phases are independently shippable, in this order (A underpins identity trust; D precedes E because E reuses D's conversion).

---

## Phase A — Integrity fixes

- `fooditem/FoodItemRepository.java`: add `findAllByUpcIncludingDeleted(String upc)`; **delete dead** `findBestMatchByName` / `candidateMatchesByName` / `wordCount` (only tests reference them; delete those tests too — file gets repurposed in Phase G).
- `barcode/UpcResolutionService.java`: make `upsertAndAlias` a real upsert — if a row with this UPC exists (prefer active, else newest soft-deleted), copy fetched name/nutrition onto the **existing id** (preserve `use_count`/`last_used_at`, clear `deleted_at`); else insert. WARN (not silent skip) when the alias key is owned by a *different* item. Extract public `findOrFetchByUpc(String upc)` (cache → OFF → FDC, upserting) used by `resolve()` and by…
- `food/LogFoodByUpcTool.java`: replace its duplicated inline OFF-fetch-and-insert with `findOrFetchByUpc`.
- `food/LogFoodTool.java` hardening: in `resolveItem`, when `useEstimate=true`, first try the exact alias lookup; on a hit, log the cached item instead of minting another MODEL_ESTIMATE (stops the greek-yogurt dupe machine).
- New migration `023-restore-food-item-upc-unique-index.yaml`: defensive SQL nulling `upc` on all-but-lowest-id per duplicate (no-op today) → explicit `createIndex unique idx_food_item_upc` (never inline column constraint — SQLite/Liquibase table-recreate drops those) → belt-and-braces explicit unique index on `food_alias(alias_normalized)`.
- New migration `024-dedupe-model-estimate-food-items.yaml`: repoint `food_entry.food_item_id` from higher-id active MODEL_ESTIMATE dupes to lowest-id same-lower(name) row, soft-delete the dupes (generic SQL; effectively 9,10 → 5).

## Phase B — Auto-log a single strong fuzzy match

- `food/resolve/FuzzyCandidateGenerator.java`: expose scores — `record ScoredMatch(FoodItem item, double score)` + `scoreCachedItems(...)`; migrate/delete `matchCachedItems`.
- `food/resolve/FoodResolution.java`: new sealed variant `AutoResolved(FoodItem item)` (compiler finds every switch site).
- `food/resolve/FoodResolver.java`: after alias miss apply thresholds — **single candidate ≥0.6**: auto-accept if score ≥0.95, or score ==0.8 (substring tier) *and* `wordCount(candidate) − wordCount(query) ≤ 1` (resurrects the guard from deleted `findBestMatchByName`; blocks "chicken" → "chicken salad sandwich" while allowing "wheat bread" → "Whole Wheat Bread"); 0.6–0.79 stays Unknown. **Multiple candidates**: auto-accept top only if top ≥0.95 and runner-up <0.8; near-tie at the top (top and runner-up both ≥0.95) → auto-pick the **higher per-100g-calorie** candidate instead of asking (philosophy: overestimate rather than interrupt), echo names the pick; otherwise Ambiguous as today.
- `food/LogFoodTool.java`: handle `AutoResolved` — write alias with `source='AUTO'`, echo gains a marker: `wheat bread (85g) → Whole Wheat Bread (MANUAL, auto-matched) — 212 cal`.
- Update javadocs (`FoodResolver`, `FuzzyCandidateGenerator`, `FoodAlias` source list) + append "Amendments (2026-09)" to `docs/fixing-substring-problem-spec.md`.

## Phase C — Learn an alias from every clarification pick

- `FoodResolver.java`: replace `confirmSelection(foodRef, id, wasAmbiguous)` with `learnAlias(foodRef, foodItemId, source)` — normalize, skip if key exists (race guard / already-pointing), save. Rewrite javadoc.
- `LogFoodTool.resolveItem`: **delete the re-resolve at line 150** (`foodResolver.resolve(...) instanceof Ambiguous` — can trigger an extra FDC network call per confirmation turn); all pick branches (`resolvedFoodItemId`, `resolvedFdcId`, `logEstimate`) call `learnAlias(..., "USER")`.

## Phase D — Calories as a quantity

- `food/resolve/QuantityResolver.java`: new anchored pattern `^(about )?N k?cal(orie)?s?$` as a tier between explicit grams and bare-number servings; `grams = kcal / per100gCalories * 100`, guard null/≤0 calories → `Unresolvable` (existing clarify branch asks for grams). Extend UPC overload to `resolve(item, quantityG, quantityServings, quantityKcal)`, precedence g > kcal > servings.
- `food/LogFoodByUpcRequest.java` + `LogFoodByUpcTool.java`: add `quantityKcal`, pass through, update tool description ("exactly one of the three").
- `intents.yaml` log_food fragment + `LogFoodTool` description: add `"250 cal"` to the verbatim amountText examples (no new schema fields on log_food — phrase stays verbatim text per the anti-hallucination design).
- `intent/PromptAssembler.java` + `intents.yaml`: new persona principle — when estimating calories/macros for unknown foods (estimate path), err a few percent **high**, never under (user philosophy: overestimation is safe, underestimation undermines the weight-loss goal).

## Phase E — Deterministic fast path (skip the LLM)

- **New** `food/resolve/FastLogParser.java` (pure/static): case-insensitive; strip trailing punctuation and an optional leading "I ate"/"ate" (NOT "yesterday…" — backdating stays with the LLM). Then anchored forms, first match wins:
  1. `^N ?g(rams?)? (of )?<food>$` → grams — covers "I ate 207 g of greek yogurt", "142g cheerios"
  2. `^N ?k?cal(orie)?s? (of )?<food>$` → calories — covers "I ate 100 cal apple sauce", "i ate 250 calories of hot dog buns"
  3. `^N <food>$` → servings; on alias miss with ≥2 remaining words, retry first word as unit: alias(remainder) + amountText `"N <unit>"` → portion-unit tier — covers "3 slices honey wheat bread"
  The **exact-alias requirement is the false-positive guard**: estimate-style entries ("…140 calories 20 g protein", "…at 240 calories each"), multi-item ("…and medium size sausage pattie"), and bare-number clarification picks ("1", "2 2") fail the alias lookup or have no food phrase → LLM untouched.
- **New** `food/FastFoodLogService.java` — `Optional<String> tryHandle(metabolicDate, occurredAt, rawText)`:
  1. parse → miss = empty; 2. **exact alias only** via `FoodAliasRepository.findByAliasNormalized(normalize(foodPhrase))` → active item (deliberately not `FoodResolver.resolve` — no FDC call on random messages; fuzzy singles still go via LLM where Phase B auto-logs); 3. `quantityResolver.resolve(item, amountPhrase)` (shared g/kcal/servings conversion — why D precedes E); 4. `fooditem.FoodItemLogger.logScaled(item, grams, occurredAt)` (reuses snapshot, group id, usage bump, macro recompute); 5. persist + feed model context via `ConversationHistoryStore.append(metabolicDate, occurredAt, rawText, reply)` (4-arg overload — verified it also pushes into the in-memory window, so a follow-up "make that 100g" still works); 6. return `logScaled`'s existing fixed `Logged "X" (142g): N kcal…` message.
- `chat/ChatController.java`: inject the service; in `replyAt`, try it **before** `withClientTimingNote`/`chatService.reply` (offline-drained "142g cheerios" then backdates via `occurredAt` and parses cleanly without the timing-note prefix). Frontend: no changes — reply flows back as a normal turn.

## Phase F — Inline quantity prompt after barcode scan

Backend:
- `barcode/UpcResolveResult` / `BarcodeDecodeResponse` / `UpcResolutionService.resolve`: add `foodItemId`, `typicalServingG` (null on miss).
- `food/FoodEntriesController.java`: **new `POST /api/food-entries`** `{foodItemId, grams|kcal|servings (exactly one), clientSentAt?}` — chosen over routing text through the fast path because logging **by id** is immune to alias collisions/shared display names and queues offline losslessly. Validate item active (404) / one positive quantity (400); `occurredAt` via `DayBoundaryService.occurredAt`; convert via Phase D's overload (400 on Unresolvable); `FoodItemLogger.logScaled`; persist chat pair via `ConversationHistoryStore.append(..., "Scanned <name>: <amount>", reply)`; return `{reply, entry}`.

Frontend:
- `store/chatSlice.ts`: `quantityPrompt: {foodItemId, name, typicalServingG} | null` + open/dismiss reducers; thunk `logScannedFood` → POST with `clientSentAt`; fulfilled → push returned pair into `messages`, close prompt; offline `TypeError` → `enqueueEntry` + client-only "queued" line. **Remove** `setDraftTextWithCursorStart` / `pendingCursorStart` machinery (scan flow was its only consumer) + the cursor effect in `MessageInput.tsx`.
- **New** `components/QuantityPromptBar.tsx`: "How much of {name}?" — number input, g / cal / servings toggle (default g; hint "1 serving = Ng" when known), Log + dismiss. Mounted in `AppShell.tsx` between `ChatWindow` and `MessageInput` (chat screen).
- `store/barcodeQueueSlice.ts` `applyUpcResolution`: hit branch dispatches `openQuantityPrompt(...)` instead of the draft prefill; miss branch unchanged (prebind → FoodItemsView). Drained queued scans: last resolved scan owns the single prompt — same semantics as today's draft.
- `components/foodItems/FoodItemsView.tsx` prebind save: `openQuantityPrompt(savedItem…)` + `setScreen('chat')` instead of draft prefill.
- `lib/offlineQueue.ts`: DB_VERSION 3, new `queued-entries` store (`{id, foodItemId, name, amount, unit, clientSentAt}`); `store/connectivitySlice.ts` drains it (`drainEntryQueue`); `OfflineQueuePanel.tsx` + header count include the third queue. Queued submits keep original `clientSentAt` → backdate correctly.

## Phase G — Alias & food-item editing fixes (independent; any time after A)

- `fooditem/FoodItemController.java`: `addAlias` — same-item existing → 200 idempotent; foreign owner → **409** with `{alias, owningItemId, owningItemName}` (structured body, not ResponseStatusException reason). New `GET /{id}/portion-units` + `DELETE /portion-units/{id}`.
- `FoodItemRepository.search`: add `OR EXISTS (SELECT 1 FROM food_alias fa WHERE fa.food_item_id = food_item.id AND fa.alias_normalized LIKE '%' || LOWER(:query) || '%')`.
- `store/foodItemsSlice.ts`: `addFoodAlias` rejects with the 409 message; `aliasError` state; portion-unit load/remove thunks.
- `components/foodItems/FoodItemsView.tsx`: un-gate the alias section from `editingId !== null` — while creating, accumulate `pendingAliases[]` locally, then on save loop `dispatch(addFoodAlias(...)).unwrap()`; on collision keep modal open in edit mode showing which alias failed. Edit-mode `handleAddAlias` gets `.unwrap()` + visible inline error (no more fire-and-forget). List + delete portion units. Search placeholder → "Search by name or alias…".

## Docs cleanup (fold into nearest phase)

`ChatService.FOOD_NUDGE` / `FoodLogClaimDetector` javadocs mention a nonexistent `log_cached_food` tool (Phase E touches that area); spec amendments (B/C); note stale `USER-GUIDE.md`.

## Tests & verification

- **Unit/integration** (follow `FoodResolverTest` SpringBootTest+temp-SQLite pattern): `UpcResolutionServiceTest` (upsert stability, foreign-alias WARN), `LogFoodToolTest` (useEstimate-with-alias logs cached item; auto-match writes AUTO alias + echo), `FoodResolverTest` (threshold matrix: sole exact / plural / substring-within-word-guard auto-resolve; "chicken" vs "chicken salad sandwich" asks; 1.0-vs-0.8 pair → Ambiguous), `QuantityResolverTest` (kcal forms, null/0-cal guard), new `FastLogParserTest` + `FastFoodLogServiceTest` (fixtures drawn from real `chat_message` history — match set: "I ate 207 g of greek yogurt", "I ate 100 cal apple sauce", "3 slices honey wheat bread"; must-not-match set: "yesterday i ate…", "…140 calories 20 g protein", "I ate 2 eggs scrambled and medium size sausage pattie", "1", "2 2", "Weight 180.4"; both chat halves persisted), `ChatControllerTest` (fast-path hit → `chatService` never invoked), `FoodEntriesControllerTest` (POST per-unit + validation), `FoodItemControllerTest` (409 shape, idempotent re-add, portion-unit GET/DELETE), repurposed `FoodItemRepositoryTest` (alias-aware search).
- **Build**: `cd backend && gradlew.bat test` and `cd frontend && npm run build && npm run lint`. Back up `backend/data/chat-diet.db` before first boot; then verify migrations: `PRAGMA index_list('food_item')` shows unique `idx_food_item_upc`; greek-yogurt 9/10 soft-deleted, their entries repointed to 5.
- **Live flows** (app serves at https://localhost:8443, dev proxy 8080): `POST /api/chat {"text":"142g orange juice"}` → instant fixed confirmation, both halves in `/api/chat/history`; `"250 cal of orange juice"` via LLM → ~556g logged; "wheat bread" with sole cached "Whole Wheat Bread" → auto-logged, AUTO alias written; ambiguous pick → re-send same phrase resolves silently; scan known UPC → quantity bar → 150g → persisted pair survives reload; scan unknown UPC → prebound modal → save → back to chat with prompt; airplane-mode quantity submit → queued → drains on reconnect; alias collision in UI → visible 409 error; `?q=<alias substring>` finds the item.