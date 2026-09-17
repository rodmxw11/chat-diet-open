# chat-diet through a harness / tools / skills lens

An architecture assessment of the repo as of 2026-09-17, organized the way you'd map an agent
framework: a fixed orchestration loop (harness), callable backend functions (tools), and
prompt-level "what to do" definitions (skills). No code changes are proposed here — this is a
review to work from before deciding what to refactor.

## 1. Harness — the orchestration loop

Request lifecycle, HTTP in to HTTP out:

1. **`ChatController.chat()`** (`chat/ChatController.java:58-63`) receives `POST /api/chat`, calls `replyAt()`.
2. **`replyAt()`** (`ChatController.java:89-97`) resolves `occurredAt`/`metabolicDate` via `DayBoundaryService`, then tries the **deterministic fast path** first: `FastFoodLogService.tryHandle(...)`. If that returns a value, the model is never invoked for this turn.
3. On a fast-path miss, `withClientTimingNote()` optionally prepends an offline-replay disclosure, then **`ChatService.reply(...)`** runs.
4. **`ChatService.reply()`** (`chat/ChatService.java:113-163`): pulls `ConversationHistoryStore.get(metabolicDate)` (a capped rolling window, see §4), builds the system prompt fresh via `PromptAssembler.systemPrompt()` (base persona + current datetime + every enabled intent's `promptFragment`), and calls the `ChatClient`. Spring AI's `ChatClient` runs its own internal tool-calling loop against the tool set bound once at construction (`chatClientBuilder.defaultTools(promptAssembler.tools())`).
5. **Post-hoc self-verification** (`ChatService.java:125-159`): after the model replies, `FoodLogClaimDetector`/`WeightLogClaimDetector` check if the reply *sounds like* "Logged: ..." while the matching request-scoped verification context shows no tool actually fired. If so, one corrective retry is sent; if still unverified, the reply is **replaced with a hardcoded honest message** rather than trusted.
6. `ConversationHistoryStore.append()` persists both turn halves to `chat_message` and to the day's in-memory deque (§4).
7. Back in `ChatController`, request-scoped side-channel beans (`ChartResultContext`, `SqlResultContext`) are read and attached to the JSON response alongside the model's text reply.

`IntentRegistry` loads `intents.yaml` once at startup; `ToolRegistry` reflectively discovers every `@IntentTool`-annotated bean and wraps it as a callable tool; `PromptAssembler.tools()` resolves each enabled intent's `toolNames` against that map (silently dropping unmatched names). This is the wiring that turns a YAML "skill" declaration into an actual LLM-callable tool.

## 2. Tools — every `@IntentTool` bean

| Tool | Inputs | Side effects | Purity / hidden state |
|---|---|---|---|
| `log_food` | items[] (foodRef, amountText, resolution hints) | Writes `food_entry`, bumps `food_item.use_count`, may cache new `FoodItem` from FDC, learns `food_alias`, recomputes `daily_macro_cache` | Heavy hidden state: alias cache, FDC network call, portion-unit learning |
| `log_food_by_upc` | upc, one of quantityG/Kcal/Servings | Upserts `FoodItem` via Open Food Facts/FDC, writes `food_entry` | External API call |
| `save_note` | text | Writes `note`, `now()` | Pure write; **ignores any occurredAt/offline backdating** |
| `log_weight` | weightLbs | Writes `weight_entry`, `now()` | Same — **no loggedAt param exists at all** |
| `log_vitals` | systolic, diastolic, heartRate? | Writes `vitals_entry`, `now()` | Same gap |
| `log_exercise` | name, duration, caloriesBurned? | Writes `exercise_entry`, `now()` | Same gap |
| `log_digestive_event` | eventType, notes? | Writes `digestive_event`, `now()` | Same gap |
| `set_calorie_goal` | targetCalories, effectiveFrom? | Writes `daily_target` | Pure given today's date |
| `get_daily_target` | — | none | Reads `daily_target` + sums today's `food_entry` |
| `get_fasting_status` | — | none | Reads latest `food_entry` |
| `get_weight_projection` | goalWeightLbs XOR goalDate | none | Reads weight trend |
| `get_tdee` | — | none | Delegates to `AdaptiveTdeeService` (14-day window) |
| `list_food_entries` | date | none | Deterministic day-lookup, built as a non-LLM alternative to `run_sql` |
| `show_chart` | from/to/granularity/metric/… | Writes into request-scoped `ChartResultContext` | Model must resolve date range/granularity itself (see §3/§5) |
| `run_sql` | question (NL, verbatim) | Runs a **nested sub-LLM** (composes SQL, validates read-only, executes, persists a `SavedQuery`) | A harness-inside-a-harness — a second full LLM round-trip per call |
| `correct_food_entry` / `correct_weight_entry` / `correct_vitals_entry` | overrides | Updates most-recent row in place, preserves prior values as JSON | Resolves target via **structured DB query**, not chat text — good pattern |

External-API-touching tools: `log_food` (USDA FDC), `log_food_by_upc` (Open Food Facts/FDC), `run_sql` (nested LLM call). Everything else is pure DB.

## 3. Skills — `intents.yaml`

Structurally it's a clean declarative shell (name/description/toolNames/enabled) around free-form prose bodies — no schema/DSL, no structured routing table; all "when to call what" logic is natural language re-interpreted every turn.

Fragments split into two behaviors:

- **Appropriate prose (routing/classification):** `save_note` vs. others, `query_data`'s dispatch across five tools, `correct_entry`'s "linguistically marked" heuristic. Genuinely ambiguous NL classification — a reasonable LLM job.
- **Prose doing computation (arguably tool-side instead):**
  - The **"chobani" special case** — fixed 190g/140cal package + fruit-remainder subtraction as arithmetic-in-prose. The clearest example: zero-ambiguity math, re-derived by the model every time.
  - The **meal-time-to-clock-time table** (breakfast=8am, lunch=12:30pm, dinner=6pm) — a static lookup expressed as prose.
  - The **chart granularity-default table** ("today"→HOUR, "this week"→DAY, etc.) — another static mapping the model must reproduce correctly rather than a deterministic phrase parser resolving it.

None of these are wrong as-is (they work, per live testing), but they're the parts most likely to drift under unusual phrasing, since correctness depends on the model re-deriving the same lookup/arithmetic each time.

## 4. State

- **Durable business state:** `food_entry`, `food_item`, `food_alias`, `portion_unit`, `weight_entry`, `vitals_entry`, `exercise_entry`, `digestive_event`, `note`, `daily_target`, `daily_macro_cache`, `saved_query`, `omron_reading` — real tables, source of truth for every query/correction/chart tool.
- **Conversation history:** `chat_message` is durable and complete (full-day transcript for the UI), but only the **last ~10 messages** of the current metabolic day are replayed into the model's context window — by explicit design ("the DB is the real memory, this stays small deliberately"). This is exactly why the loggedAt date-bleed bug already tracked in memory exists: the model infers dates from a short rolling window, not full history.
- **Request-scoped Java state:** `ChartResultContext`, `SqlResultContext`, and the two verification contexts exist purely to smuggle a structured tool result (or a "did a tool actually fire" boolean) out of the tool-calling loop into the HTTP response, bypassing the model's own prose. Deliberate and reasonable, not a smell.
- **Nothing conflated:** correction tools resolve targets by querying the real tables directly, never by re-parsing chat text. Used consistently.
- **One real gap:** `log_weight`, `log_vitals`, `log_exercise`, `log_digestive_event`, `save_note` all hardcode `now()` with **no loggedAt parameter at all** — unlike `log_food`, which threads it through from the controller. An offline-queued weight or BP reading composed hours earlier will silently misdate to "now." Same bug class as the food date-bleed issue, but structural here (no parameter exists) rather than an LLM inference slip.

## 5. Architectural smells

- **Soft overlap, self-documented:** `list_food_entries` vs `run_sql` for "what did I eat" questions — `list_food_entries`'s own Javadoc admits the model doesn't reliably route to it, so it exists as a redundant deterministic escape hatch. An honest, already-acknowledged smell.
- **Untested tool wrappers:** only `LogFoodTool` and `CorrectFoodEntryTool` have direct tests; the other dozen-plus tools are covered only indirectly (through well-tested underlying services) or through `IntentRoutingTest`, which is `@EnabledIfEnvironmentVariable`-gated on a live API key — meaning there's no deterministic, always-on routing test.
- **Missing tool-call observability:** no centralized log of "which tool was called, with what args, chosen over which alternative." Debugging routing decisions today means enabling Spring AI's own debug logging.
- **LLM doing deterministic work (beyond chobani):** the meal-time defaults and chart-granularity defaults are both static tables the model must reproduce from prose every time, rather than a parser resolving them before the model sees the request. `correct_entry`'s ambiguity detection is harder to fully deterministic-ify, but an obvious-keyword prefilter ("make that...", "actually...") could resolve the unambiguous majority before falling back to the model — mirroring how `FastFoodLogService` already does this for plain food logging.
- **The fast-path / prompt-fragment coordination gap:** Verified directly. `FastLogParser`'s grammar requires the message to open with a **number**, so "chobani 240g" doesn't match it and correctly falls through to the LLM today. But the two layers aren't actually coordinated — they just haven't collided yet. If phrasing were ever amount-first ("240g chobani"), **and** a bare one-word "chobani" alias got auto-learned into `food_alias` (which happens automatically the moment the model ever passes a literal `foodRef: "chobani"`, e.g. from "I had chobani for breakfast" with no fixed name), `FastFoodLogService` would silently intercept it and apply straight-line scaling (240/190 × 140 ≈ 177 cal) instead of the fixed-package-plus-fruit split — with no error, no log, no way for the chobani prose rule to ever run. The special case lives entirely in a layer the fast path has no visibility into.

## If I had to refactor one thing first

I'd harden the fast-path/prompt-fragment boundary before anything else: either teach `FastFoodLogService`/`FastLogParser` to recognize and skip aliases with model-side special handling (a flag on `FoodItem`/`FoodAlias`), or block a bare "chobani" alias from ever being learned. Right now the deterministic layer and the prose "skill" layer are invisible to each other, and this is the one spot found where that invisibility could silently produce a wrong number with zero error signal — exactly the failure mode `ChatService`'s claim-verification retry was built to catch elsewhere, but doesn't cover here since the fast path never touches the model at all.
