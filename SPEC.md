# chat-diet — Design Specification

A single-user conversational app for logging food, weight, vitals, and
exercise, with nutrition math and ad-hoc analytics. Chat is the primary
interface; a handful of read-only pages, one deliberate data-entry
exception (Food Items), and a permanent dashboard sit alongside it for
browsing and at-a-glance totals.

Reflects the app as implemented. Originally written as a pre-implementation
brief; this revision replaces that draft after a simplification pass dropped
or reshaped several subsystems (see the Appendix for what changed and why).

---

## 1. Constraints and principles

| Constraint | Value |
|---|---|
| Users | Exactly one (the owner). No auth beyond network isolation. |
| Access | Tailscale on home network. No public exposure. |
| Deployment | Single Docker container on a home server; HTTPS served directly via a Tailscale-issued cert. |
| Backend | Java 21 on Spring Boot, Spring AI for Anthropic. |
| Frontend | React + Redux PWA, light/dark theme, Recharts. |
| Database | SQLite (file-mode), Liquibase-managed schema. |
| Build | Gradle. Monorepo (backend + frontend). |
| LLM | Claude API only. No local models. |
| API budget | Under $10/month. This is a hard design driver. |
| Units | Imperial (lbs, oz, cups). |
| Time | Local time only. No timezone handling. |

### Java 21 conventions

| Feature | Where |
|---|---|
| Records | Tool request/response DTOs, entities (via Spring Data JDBC, not JPA — no mutable-field/no-arg-constructor requirement to work around), chart series. |
| Sealed interfaces + pattern matching | `ToolResult` variants. Exhaustive `switch` over enum-plus-if-chains. |
| Text blocks | Prompt fragments, base system prompt, SQL-agent schema DDL block. |
| Streams | Nutrition aggregation, daily bucketing, chart series computation. |
| `var` | Locals where the right-hand side makes the type obvious. |

Avoid Lombok. Records cover most of what it was used for, and the annotation
processor is one more thing to break.

### Behavioral principles

These are not features. They constrain every response the app produces.

1. **The app is not a nanny.** It never volunteers commentary on data — not
   out-of-range vitals, not calorie progress, not food choices. It answers what
   is asked and logs what it is told.
2. **Echo every number.** Any numeric value persisted is read back in the
   response so transcription errors are visible immediately.
3. **Save immediately, correct after.** No confirmation gate before writing.
4. **Corrections must be linguistically marked.** "The correct BP is 156 over
   65", "make that two slices". A bare number is *always* a new entry, never
   inferred as a correction.
5. **Target ~80% nutrient accuracy for free-text estimates.** The scale is
   ground truth; the food log is a noisy estimator. Drift is expected. UPC
   lookups are the exception — those carry real Open Food Facts data, not an
   estimate.
6. **Sub-10-calorie items are not logged.** Black tea, water. Brief
   acknowledgement, no row written.

---

## 2. Architecture

A single agent with a tool belt, not an orchestrator with sub-agents. Intents
are data (YAML), so the extension seam for future specialization is promoting
an intent group to its own prompt/toolset without restructuring anything.

```mermaid
graph TB
    subgraph Client["React + Redux PWA"]
        Chat[Chat stream]
        Pages[Daily Foods / Chat History /<br/>Notes / Micronutrients /<br/>Food Items / DB Schema]
        Dashboard[Dashboard cards<br/>macro chart + weight trend + TDEE stat]
        Queue[IndexedDB offline queue]
        Tables[Result table + CSV]
    end

    subgraph Backend["Spring Boot / Java 21"]
        API[REST controllers]
        CS[ChatService]
        IR[IntentRegistry<br/>YAML-loaded]
        PA[PromptAssembler]
        TR[ToolRegistry<br/>annotation-scanned]
        Tools[Tool beans]
        Cache[DailyMacroCacheService]
        SQL[SqlAgentService]
        TDEE[AdaptiveTdeeService]
        Cost[ChatCostCalculator]
        Backup[BackupJob / ExportService]
    end

    subgraph External["External"]
        Haiku[Claude Haiku<br/>main loop]
        Opus[Claude Opus<br/>SQL composition]
        OFF[Open Food Facts<br/>UPC-identified products]
        FDC[USDA FoodData Central<br/>named raw/generic foods]
    end

    DB[(SQLite)]

    Chat --> API
    Queue --> API
    API --> CS
    CS --> IR
    IR --> PA
    PA --> Haiku
    CS --> TR
    TR --> Tools
    Tools --> DB
    Tools --> Cache
    Cache --> DB
    Tools --> OFF
    Tools --> FDC
    Tools --> SQL
    Tools --> TDEE
    TDEE --> DB
    SQL --> Opus
    SQL --> DB
    Cost --> DB
    Backup --> DB
    API --> Pages
    API --> Dashboard
    API --> Tables
```

### Model tiering

Cost discipline lives here.

| Model | Used for | Frequency |
|---|---|---|
| Haiku (`claude-haiku-4-5`) | Main chat loop, all logging and queries | Every turn |
| Opus (`claude-opus-4-5`) | SQL composition from natural language | Rare — saved-query reuse handles the common case |

Token usage is recorded per turn on `CHAT_MESSAGE` (separate Haiku/Opus
columns, since Opus usage is a per-`run_sql`-call sum within the turn, not
per-message). A `MODEL_PRICING` table holds a flat USD-per-million-token
rate for each model (editable directly if published rates change);
`ChatCostCalculator` turns a day's token totals into a rounded-to-the-cent
dollar figure, surfaced on the Chat History page.

---

## 3. Data model

One row per logged utterance — no line-item/multi-ingredient breakdown, no
recipe system, no per-item cost tracking.

```mermaid
erDiagram
    FOOD_ENTRY }o--|| FOOD_ITEM : "may reference (cache/FDC/OFF reuse, or a freshly cached model estimate)"
    PORTION_UNIT }o--|| FOOD_ITEM : "known unit -> grams mappings"

    FOOD_ENTRY {
        long id PK
        datetime logged_at
        string raw_utterance
        int total_calories
        real total_protein_g
        real total_carbs_g
        real total_fat_g
        real fiber_g
        real sugar_g
        real sodium_mg
        real saturated_fat_g
        real cholesterol_mg
        real potassium_mg
        long food_item_id FK
        real amount_grams
        int prep_minutes
        string source
        datetime corrected_at
        string prior_values_json
    }

    FOOD_ITEM {
        long id PK
        string name
        string upc
        real per100g_calories
        real per100g_protein
        real per100g_carbs
        real per100g_fat
        real per100g_fiber
        real per100g_sugar
        real per100g_sodium_mg
        real per100g_saturated_fat
        real per100g_cholesterol_mg
        real per100g_potassium_mg
        real typical_serving_g
        string lookup_source
        int use_count
        datetime last_used_at
        datetime deleted_at
    }

    WEIGHT_ENTRY {
        long id PK
        datetime logged_at
        real weight_lbs
        datetime corrected_at
    }

    VITALS_ENTRY {
        long id PK
        datetime logged_at
        int systolic
        int diastolic
        int heart_rate
        datetime corrected_at
    }

    EXERCISE_ENTRY {
        long id PK
        datetime logged_at
        string exercise_name
        int duration_minutes
        int calories_burned
    }

    DIGESTIVE_EVENT {
        long id PK
        datetime logged_at
        string event_type
        string notes
    }

    NOTE {
        long id PK
        datetime logged_at
        string text
    }

    DAILY_TARGET {
        long id PK
        date target_date
        int target_calories
    }

    DAILY_MACRO_CACHE {
        long id PK
        date metabolic_date
        int total_calories
        real protein_g
        real carbs_g
        real fat_g
        datetime updated_at
    }

    SAVED_QUERY {
        long id PK
        string name
        string description
        string sql_text
        string param_defs_json
        int use_count
        datetime last_used_at
    }

    CHAT_MESSAGE {
        long id PK
        date metabolic_date
        string role
        string content
        datetime created_at
        int prompt_tokens
        int completion_tokens
        int total_tokens
        int opus_prompt_tokens
        int opus_completion_tokens
        int opus_total_tokens
    }

    MODEL_PRICING {
        long id PK
        string model
        real input_cost_per_million_usd
        real output_cost_per_million_usd
    }

    PORTION_UNIT {
        long id PK
        long food_item_id FK
        string unit_name
        real grams
        string source
    }
```

### Notes on the model

- **`source`/`lookup_source`** on `FOOD_ENTRY`/`FOOD_ITEM` is a free-text tag
  (`MANUAL`, `OFF`, `FDC`, `MODEL_ESTIMATE`) — not a closed enum. A named-food
  logging tier that produces real per-100g data (a cache hit, an FDC match, or
  even the model's own one-time estimate) caches or updates a `FOOD_ITEM` row
  tagged with whichever tier produced it, so the next mention of the same food
  resolves deterministically instead of re-estimating.
- **Micronutrients** (fiber, sugar, sodium, saturated fat, cholesterol,
  potassium) live on both `FOOD_ENTRY` and `FOOD_ITEM`. For free-text logging
  with no cache/FDC match the model estimates them alongside calories/macros;
  for UPC- or FDC-identified foods they come from the respective external
  source and are scaled deterministically by portion size, same as the
  macros.
- **`FOOD_ITEM`** is a nutrition-lookup/reuse cache (by UPC or fuzzy name
  match), not a recipe or multi-ingredient concept — logging a cached item
  just scales its per-100g values by the amount eaten. It has its own
  management page (see §10) for fixing bad/partial cached data directly,
  including two lookup shortcuts (USDA FDC by name, Open Food Facts by UPC)
  and manual entry at any gram amount (auto-scaled to per-100g on save).
- **`FOOD_ITEM.deleted_at`** is a soft delete: a deleted item stops matching
  new lookups but stays resolvable for past `FOOD_ENTRY` rows that already
  reference it, and can be restored from the management page's Deleted tab.
- **`FOOD_ENTRY.food_item_id`/`amount_grams`** record which cached item (if
  any) an entry was scaled from and the weighed amount used. A gram-based
  correction (`correct_food_entry` with a new weight) resolves this reference
  and recomputes from the cached item's current per-100g values rather than
  the model guessing new totals — see §6.
- **`DAILY_TARGET`** is a manually-set calorie goal, effective from
  `target_date` until a later goal supersedes it (carry-forward semantics).
  No computed fields, and nothing writes to it automatically — the separate,
  read-only Adaptive TDEE estimate (§7) never adjusts it.
- **`MODEL_PRICING`** is a flat, non-versioned USD-per-million-token rate per
  model, edited directly if Anthropic's published rates change — not a
  billing reconciliation, just enough to turn `CHAT_MESSAGE` token counts
  into an approximate daily cost figure.
- **Corrections** are in-place updates with `corrected_at` plus
  `prior_values_json` (a full JSON snapshot of the row before the correction)
  as an audit trail. Not delete-and-reinsert.
- **No clothing-state on weight entries.** Considered and rejected: clothing
  variance is smaller than time-of-day variance and both wash out over the
  trend. The timestamp is the time-of-day metadata.
- **Shopping list retired.** `SHOPPING_ITEM` (built in an earlier phase) is
  gone from the model entirely — see the Appendix.

### Day boundary

Daily totals roll over at a **configurable hour, default 04:00**
(`chat-diet.day-rollover-hour`) — not calendar midnight. Late-night eating
counts toward the prior day.

The metabolic day is **also the conversation boundary** — there is no separate
session concept. Every device and channel writing on the same metabolic day
appends to the same thread; `ConversationHistoryStore` rehydrates a day's
context from `CHAT_MESSAGE` on first access rather than keeping it resident.

---

## 4. Intent registry

Intents are **data, not code**, loaded from `intents.yaml` at startup and
grouping tool beans discovered by component scan.

```java
public record IntentDefinition(
        String name,
        String description,
        List<String> toolNames,
        String promptFragment,
        boolean enabled
) {}
```

The system prompt is **assembled, not hand-written**:

```
[base persona + behavioral principles from §1]
+ [current date, time, day-rollover hour]
+ for each enabled intent: promptFragment
+ [tool definitions: union of enabled intents' toolNames]
```

Adding an intent is one YAML block plus any new tool beans. Zero edits to
existing intents, to the base prompt, or to `ChatService`.

### Current intents

| Intent | Purpose |
|---|---|
| `log_food` | Named-food logging (cache → USDA FDC → LLM estimate, in that order when a gram amount is given), UPC logging (Open Food Facts), and cached-item reuse |
| `log_weight` | Weight entries |
| `log_vitals` | BP / heart rate |
| `log_exercise` | Exercise by name |
| `log_digestive_event` | Reflux, diarrhea, etc. |
| `save_note` | Freeform timestamped notes-to-self |
| `manage_goal` | Set the manual daily calorie target |
| `correct_entry` | Marked corrections to any recent entry, including gram-based food corrections that recompute from a cached item |
| `query_data` | Totals, fasting duration, weight projection, adaptive TDEE, current target, entry lookups |
| `show_chart` | Ad-hoc chart requests with flexible time frames |
| `run_sql` | Natural-language → SQL → result table |

`capture_requirement` and `recipe_intake` (both present in an earlier design)
no longer exist, and `manage_shopping` was later built and then removed —
see the Appendix.

### Current tool roster

`log_weight`, `correct_weight_entry`, `log_vitals`, `correct_vitals_entry`,
`log_exercise`, `log_digestive_event`, `save_note`, `log_food`,
`log_food_by_upc`, `log_cached_food`, `correct_food_entry`,
`list_food_entries`, `set_calorie_goal`, `get_daily_target`,
`get_weight_projection`, `get_fasting_status`, `get_tdee`, `show_chart`,
`run_sql` — 19 tools total. Note `log_cached_food` (servings-aware, for
naming a previously UPC-scanned product by name — "another Clif bar") is a
distinct, model-visible tool from `log_food`'s own internal, amount-in-grams-
only cache/FDC pre-check (see §6); they solve overlapping but not identical
cases.

### Tool discovery

```java
public sealed interface ToolResult {
    record Success(String message, Object payload) implements ToolResult {}
    record NeedsClarification(String question, Object partial) implements ToolResult {}
    record NotFound(String what) implements ToolResult {}
}

@Component
@IntentTool(name = "save_note", intents = {"save_note"}, description = "...")
public class SaveNoteTool implements Function<SaveNoteRequest, ToolResult> {
    @Override
    public ToolResult apply(SaveNoteRequest req) {
        // ...
    }
}
```

`ToolResult` as a sealed interface gives exhaustive pattern matching in
`ChatService` when marshalling results back to the model. Component scan +
annotation means the registry discovers tools rather than maintaining a
manual mapping. New tool = new class.

---

## 5. Chat lifecycle

```mermaid
sequenceDiagram
    actor U as User
    participant P as PWA
    participant C as ChatService
    participant R as IntentRegistry
    participant H as Claude Haiku
    participant T as Tool beans
    participant D as SQLite

    U->>P: utterance (voice or text)
    P->>C: POST /api/chat {text, clientSentAt}
    C->>D: rehydrate today's context if not cached
    C->>R: enabled intents
    R-->>C: prompt fragments + tool defs
    C->>H: assembled prompt + context + tools
    H-->>C: tool_use request(s)
    C->>T: dispatch
    T->>D: read / write
    T-->>C: structured result
    C->>H: tool results
    H-->>C: final response (numbers echoed)
    C->>D: persist message pair
    C-->>P: response + chart/table payload if any
    P-->>U: render
```

### Daily virtual sessions

- Sessions are **implicit**: one per metabolic day, created lazily on the
  day's first message. No button, no timeout, no explicit end.
- All channels — PWA, desktop, voice — share the same daily conversation, so
  the thread follows the user across devices rather than per tab.
- A turn is filed under the day it was **composed**, not the day it arrived,
  so a message queued offline before the rollover stays with its own day.
- The model is replayed the **last `chat-diet.chat.context-messages`
  messages** (default 10) of the current metabolic day. `GET
  /api/chat/history` (optionally with a `date` param, for the Chat History
  page) returns the whole day for display, plus that day's approximate
  Haiku/Opus cost (see the Model tiering note in §2). Chart and SQL-table
  attachments are not persisted, so a restored transcript is text-only — the
  prose reply still carries the numbers.
- **The DB is the real memory.** Chat context stays short; recall of earlier
  facts happens through DB-reading tools, not by keeping long transcripts in
  context. This is the primary cost control.

### Offline

An explicit app-level **IndexedDB queue** (`frontend/src/lib/offlineQueue.ts`)
holds writes composed while the backend is unreachable and replays them on
reconnect, in order. This replaced an earlier Workbox background-sync queue
specifically because the queue panel (a header-menu overlay) needs to list
and remove individual pending messages, which Workbox's queue can't do. Log
entries carry client-side timestamps so queued entries land at the time they
happened, not the time they synced.

---

## 6. Food logging paths

```mermaid
stateDiagram-v2
    [*] --> Utterance
    Utterance --> NamedWithGrams: named food, weighed amount in grams
    Utterance --> NamedWithUnit: named food, natural count (no grams) - "2 eggs", "1 medium banana"
    Utterance --> NamedOther: named food, no amount at all
    Utterance --> UpcPath: UPC (typed, spoken, or camera-scanned)
    Utterance --> CachedByName: names a previously UPC-cached product

    NamedWithGrams --> CacheCheck: FoodItemRepository best-match-by-name
    CacheCheck --> Scale: cache hit
    CacheCheck --> FdcCheck: cache miss
    FdcCheck --> CacheFdcItem: USDA FDC plausible match
    CacheFdcItem --> Scale
    FdcCheck --> Estimate: no FDC match either
    NamedWithGrams --> LearnPortion: quantity+unit also given - upsert PORTION_UNIT (WEIGHED)

    NamedWithUnit --> CacheCheck2: same cache/FDC match as above
    CacheCheck2 --> PortionCheck: item found (cached or freshly FDC-cached)
    CacheFdcItem --> FetchPortions: fetch FDC foodPortions once, store all as PORTION_UNIT (FDC)
    PortionCheck --> Scale: unit resolves (exact, or unambiguous substring)
    PortionCheck --> Estimate: unit unknown, or no item found at all

    NamedOther --> Estimate: model estimates calories, macros, micronutrients
    Estimate --> CacheEstimate: amountGrams known - cache as FOOD_ITEM (MODEL_ESTIMATE)
    CacheEstimate --> Persist
    Estimate --> Persist: amountGrams unknown - not cached

    UpcPath --> Decode: camera photo only - ZXing, full res
    Decode --> Lookup: Open Food Facts
    Lookup --> CacheOffItem: create/update FOOD_ITEM (real macro + micronutrient data)
    CacheOffItem --> Scale

    CachedByName --> Scale: FoodQuantity resolves servings/grams

    Scale --> Persist: scale per-100g values by amount eaten
    LearnPortion --> Persist
    Persist --> Echo: read numbers back
    Echo --> [*]
    Persist --> UnderTen: <10 cal
    UnderTen --> [*]: ack, no row
```

A camera button in the header (chat screen only) captures a barcode photo,
POSTs it to `POST /api/barcode/decode` (ZXing, server-side, full resolution —
downscaling destroys barcode density), and relays the decoded UPC into chat
as plain text — reusing the same `log_food_by_upc` path a typed or spoken UPC
already goes through, rather than adding a separate mutation route.

**Two external nutrition sources, split by product type.** Open Food Facts
(`OpenFoodFactsClient`) supplies real calories, macros, and micronutrients
for **UPC-identified branded/packaged products** — meaningfully more
accurate than a free-text estimate, and free. **USDA FoodData Central**
(`FdcClient`, `Foundation`/`SR Legacy` data types only) covers **named
raw/generic foods** the same way (e.g. "42g of celery" or "1 medium
banana") — either a weighed gram amount or a resolvable natural
quantity+unit makes the lookup worth trying; with neither, there's nothing
to scale a per-100g figure to, so resolution goes straight to the model's
estimate. FDC requires a free `api.data.gov` key (`chat-diet.fdc.api-key`);
unset, the lookup tier is silently skipped.

**`PORTION_UNIT` turns a natural count into grams.** A gram amount alone
only covers utterances where the user (or model) states a weight - the
majority of ordinary phrasing ("2 eggs", "a cup of rice") never did.
`PORTION_UNIT(food_item_id, unit_name, grams, source)` maps a normalized
unit word to a gram weight per cached food, populated two ways: **from
FDC** (`FdcClient.fetchPortions`, a separate call to the single-food detail
endpoint - the search endpoint's `foodMeasures` field is empty for
Foundation/SR Legacy foods, so this costs one extra request, but only once
per newly FDC-cached item, not per log) and **from the user's own weighed
entries** (`PortionUnitService.learnFromWeighedEntry` - if a request states
*both* a gram amount and a quantity+unit, e.g. "2 eggs, about 100g", the
derived 50g/egg gets stored too). Matching a stated unit against known
portions prefers an exact normalized match, and only accepts a
bidirectional-substring match when it's unambiguous - FDC often reports
several preparations of the same rough word ("cup, sliced" vs. "cup,
mashed" both contain "cup" but differ by 75g), and picking one would be a
silent guess, not a resolution.

*(Observed in practice: Claude Haiku, even when instructed not to, tends to
pair a natural quantity+unit with its own confident gram guess rather than
omitting the gram amount to force a real FDC lookup - so the dominant
realized benefit today is the self-learning path, not the pure
FDC-portion-lookup path. Both still teach `PORTION_UNIT` real data either
way, but the "never touches the LLM estimator" outcome the pure lookup path
promises isn't yet the common case in live use.)*

**Match-confidence, not just "first result."** Both `FoodItemRepository`'s
cache lookup and `FdcClient` use the same bidirectional-substring test
(query contains the candidate's name, or vice versa) to reject an
implausible top result rather than trusting it blindly. `FdcClient`
additionally sorts plausible FDC matches by a cheap "qualifier count" (comma/
whitespace token count in the description) so a plain query like "banana"
prefers "Bananas, raw" over a processed variant like "Bananas, dehydrated,
or banana powder" that FDC's own relevance ranking might rank first — this
matters most for the fully-automated tier (`search()`, used by chat logging,
picks one match with no human to disambiguate); the food-item form's lookup
(`searchCandidates()`) shows up to 5 candidates for a person to pick from
instead.

**Every named-food logging path that produces real per-100g data caches or
reuses a `FOOD_ITEM` row** (cache hit, FDC match, OFF match, or the model's
own one-time estimate when an amount - gram or resolved unit - was known)
— so the same food resolves deterministically on its next mention rather
than re-estimating every time, and a systematic bias in the model's
estimates gets captured once instead of repeated indefinitely. One real
caveat to that: the cached row's name is whatever free-text `description`
the model passed (sometimes the whole phrase, e.g. "1 medium banana"
rather than bare "banana"), and the fuzzy name-match tier only accepts a
candidate up to one word longer than the query - so this determinism holds
best when the model phrases the same food consistently, not across
arbitrarily different wordings of it.

**Photo-based plate analysis and a recipe system were both part of an earlier
design and do not exist today** — see the Appendix.

---

## 7. Nutrition math

Deterministic where it exists. **Implemented as Java services and exposed as
tools — never performed by the model.** An LLM doing arithmetic is a defect.

### Calorie target

Purely manual. `set_calorie_goal` writes a `DAILY_TARGET` row effective from a
given date (defaulting to today); `GoalService.targetFor(date)` resolves the
target in effect for any date as the most recently set goal on or before it
(carry-forward semantics — adjusting "today's" goal upserts in place rather
than accumulating rows). **Nothing writes to `DAILY_TARGET` automatically** —
see Adaptive TDEE below for why that stays true even though a TDEE estimate
now exists.

`chat-diet.goal.weekly-rate-lbs` (config, not a chat-settable value) feeds two
things independently of the calorie target: the weight-trend chart's goal
line, and the weight-projection tool. Negative to lose weight, positive to
gain, 0 for maintenance.

### Adaptive TDEE

A **read-only** estimate of total daily energy expenditure, back-calculated
from the user's own data instead of a generic BMR formula:
`TDEE ≈ avgDailyIntake - (slope × 3500)` over a 14-day rolling window, where
`slope` (lbs/day) comes from an **OLS fit of the window's smoothed weight
trend values** (§9's EWMA), not a two-point endpoint difference — a bare
`trend(day14) - trend(day0)` uses only 2 of the 14+ points and is the
highest-variance way to estimate a slope for a given window width; fitting a
line through every real trend point in the window uses all of it instead.
The regression's standard error (converted to calories/day) is reported
alongside the point estimate, not just a bare number.
`AdaptiveTdeeService.estimate()` requires at least 7 real (non-carried-
forward) trend points and at least 7 of the last 14 days logged, or returns
a plain-English reason instead of a number. Surfaced via `get_tdee` in chat
and a stat line on the Weight Trend chart — never auto-adjusts
`DAILY_TARGET`.

**3500 kcal/lb is a fat-mass constant, and it doesn't hold for the first
couple weeks of a new deficit/surplus** (glycogen and water dominate the
scale before fat mass does). Rather than gate the estimate on a "phase"
concept this app doesn't otherwise track, `AdaptiveTdeeService` uses the
`DAILY_TARGET` history as a proxy: if the calorie goal changed within 21
days before the window starts, the estimate still computes but carries a
caveat string (shown in both the chat reply and the chart stat line)
warning that the number may reflect water/glycogen shifts rather than real
fat-mass change. Imperfect — a phase can start without a goal change — but
it's the only "started something new" signal already in the schema, rather
than inventing a new one.

This is a deliberate revival of a previously-removed idea in a narrower
shape: the original design closed the loop (auto-adjusting the effective
TDEE, and by extension the target) and was removed for oscillating on
sparse weigh-ins without damping (see the Appendix). Using the smoothed
trend rather than raw weigh-ins, and staying strictly read-only, avoids that
failure mode without needing to design the damping logic the original
lacked — the user decides whether and how to act on the number via
`set_calorie_goal`.

### Projection

Goal-date/goal-weight projection reports in pounds alongside calories wherever
a surplus or deficit is shown — `1,500 cal ≈ 0.4 lb` reads very differently
from `1,500 CALORIE SURPLUS`, and the smaller framing is the accurate one. No
plateau or stall flagging; trend line only.

### Fasting

Window auto-derived from first and last logged food that day. Answerable on
demand ("how long since I ate"). **No fasting recommendations, no compensation
planning** — the user manually adjusts based on weight feedback.

---

## 8. SQL agent

Replaces essentially all read-only analytics UI. "Favorite dinners" is a
query, not a screen.

```mermaid
sequenceDiagram
    participant U as User
    participant A as SqlAgentService
    participant S as SAVED_QUERY
    participant O as Claude Opus
    participant D as SQLite (read-only conn)

    U->>A: "what are my most common dinners?"
    A->>S: list saved queries (name + description)
    S-->>A: candidates
    alt existing query fits
        A->>O: extract params for this saved query
        O-->>A: bound params
    else nothing fits
        A->>O: compose parameterized SQL (schema DDL in prompt)
        O-->>A: SQL + param defs
        A->>S: save with auto description
    end
    A->>A: validate: SELECT/WITH only, single statement
    A->>D: execute prepared statement, 20,000-row safety cap
    D-->>A: result set
    A-->>U: SQL text + table (first 500 rows) + CSV link for the rest
```

### Non-negotiables

- **Read-only enforced at the connection.** `ReadOnlySqlExecutor` opens a
  dedicated read-only JDBC connection (`ACCESS_MODE_DATA=r`) with a hard
  20,000-row safety limit. `SqlValidator` is a second, cheaper layer: regex
  rejects blank/multi-statement input and anything that isn't a
  `SELECT`/`WITH` (blocking `INSERT`/`UPDATE`/`DELETE`/`DROP`/`ALTER`/etc. by
  keyword). Neither layer alone is sufficient.
- **Always display the generated SQL.** The user is a 40-year engineer and
  will spot a wrong join faster than a wrong number. This is the entire
  safety model.
- **Display cap of 500 rows** inline, with a `truncated` flag; the full
  result (up to the 20,000-row safety cap) is cached server-side and
  downloadable as CSV.
- **Parameterized SQL, not literals.** `WHERE logged_at >= ?`, not a baked-in
  date. This is what makes queries reusable across time frames.

### Schema DDL in prompt

Generated from the Liquibase-managed schema so the agent never composes
against columns that no longer exist.

### Why Opus is affordable here

Reuse collapses the expensive path. After warm-up, composing new SQL happens
rarely; the common case is matching a saved query and extracting parameters.
The saved-query library becomes the user's accreting analytics vocabulary,
and it exports with the DB.

---

## 9. Charts

Backend computes all series. Frontend renders only. Two distinct surfaces:

1. **Ad-hoc chart requests** via chat (`show_chart`) — the model resolves the
   time frame and granularity from the utterance itself.
2. **A permanent dashboard** (sidebar on desktop, bottom sheets on mobile) with
   two always-available cards: a 7/30-day calories-and-macros bar chart (with
   an average-calories reference line shown as a %-of-header caption) and a
   weight-trend chart (exponentially smoothed, TrendWeight-style, with a
   goal-line overlay derived from `chat-diet.goal.weekly-rate-lbs`, plus a
   stat line showing the current Adaptive TDEE estimate or why one isn't
   available yet — see §7).

```java
public enum ChartMetric { CALORIES, MACROS, WEIGHT, DEFICIT }
public enum Granularity { HOUR, DAY, WEEK, MONTH }

public record ChartRequest(
        ChartMetric metric,
        LocalDate from,
        LocalDate to,
        Granularity granularity,
        boolean includeGoal,
        boolean cumulative
) {}

public record SeriesPoint(Instant at, double value, Double target) {}
public record ChartSeries(String label, List<SeriesPoint> points) {}
```

**Period parsing is the model's job.** Give it the current date; let it fill
`from`, `to`, and `granularity` from "today", "this week", "last 3 months",
"since June".

**Intraday charts are cumulative against target**, not per-hour bars — hourly
buckets produce a step function that reads poorly.

Ad-hoc charts render inline in the chat stream, shown on request only, never
volunteered. No commentary on what a chart shows.

---

## 10. Frontend

React + Redux PWA. Chat remains the primary way to *write* diet/health data —
there is no general form-based entry — but several read-only pages, one
deliberate data-entry exception, and a permanent dashboard sit alongside it.

**Screens** (`uiSlice.ts`): `chat`, `notes` (View Notes), `foods` (Daily
Foods — date-scoped table of a day's food entries, with delete),
`chatHistory` (date-scoped read-only transcript of a past day's
conversation, with that day's Haiku/Opus cost in the header), `micronutrients`
(a day's fiber/sugar/sodium/saturated fat/cholesterol/potassium totals
against %DV bars), `foodItems` (Food Items — the one data-entry exception;
see below), `schema` (Database Schema — every table's columns and SQLite
types, introspected live via `sqlite_master`/`PRAGMA table_info`, for
browsing without a separate DB tool). The three date-scoped screens (Daily
Foods, Chat History, Micronutrients) share a merged header (title + date-nav
row in one fixed block) and the first and third reuse the same `foodEntries`
slice/date, so they stay in sync when switching between them; Food Items and
Database Schema use a plain (non-stacked) header instead, since neither is
date-scoped. `shop` (shopping mode) existed in an earlier phase and is gone —
see the Appendix.

**Food Items is the deliberate exception to "no form-based entry."** Fixing a
bad or partial cached `FOOD_ITEM` row (a UPC lookup missing sodium, a stale
model estimate) had no other path before this page existed. It supports:
searching active/deleted items; editing the full nutrient set directly; a
USDA FDC lookup by name (auto-fills on a single plausible match, shows a
picker for several — e.g. "canned beans"); an Open Food Facts lookup by UPC;
manual entry at any gram amount (e.g. straight off a nutrition label),
auto-scaled to per-100g on save; and soft-delete/restore.

**Overlays**: `chartsMacros` and `chartsWeight` (the dashboard cards, as a
bottom sheet on mobile) and `queue` (the offline-queue panel).

**Theme**: a persisted light/dark toggle in the header menu, defaulting to the
OS/browser preference until explicitly overridden.

**Quick-entry buttons** above the message input (`MessageInput.tsx`) prefill
common turn openers rather than sending anything: **Note** ("Note that "),
**Ate** ("I ate "), **Weight** ("Weight "), **Query** ("Run SQL query that ")
— added once each pattern showed up often enough in real chat history to be
worth a shortcut; `run_sql` covers "what did I eat" style questions directly
now that `FOOD_ENTRY` exists, so no separate button was added for that.

Components:

- **Chat stream** — messages, inline Recharts, inline result tables with CSV
  download
- **Push-to-talk** — Web Speech API for input; TTS output as an optional
  toggle
- **Barcode camera button** — `<input type="file" capture="environment">`,
  POSTs to the decode endpoint and relays the UPC into chat
- **Offline queue** — explicit IndexedDB-backed queue (see §5), with a panel
  to view/remove individual pending messages

---

## 11. Deployment

```mermaid
graph LR
    subgraph Home["Home server"]
        BE[chat-diet backend<br/>Spring Boot, serves API + built PWA]
        VOL[(Volume:<br/>SQLite + backups)]
        BK[Nightly BackupJob]
    end

    TS[Tailscale] -.HTTPS, Tailscale-issued cert.-> BE
    BE --> VOL
    BK --> VOL
    BE -.HTTPS.-> ANT[api.anthropic.com]
    BE -.HTTPS.-> OFF[Open Food Facts]
    BE -.HTTPS.-> FDC[USDA FoodData Central]

    Phone[Phone] --> TS
    Desktop[Desktop] --> TS
    Tablet[Tablet] --> TS
```

- **One container.** The Spring Boot backend serves both the API and the
  built frontend; no separate nginx/web tier, no Docker Compose file. HTTPS
  is served directly on 443 using a Tailscale-issued certificate
  (`TAILSCALE-certificates.md`) — no reverse proxy.
- SQLite lives on a mounted volume as a single `.db` file.
- **Nightly automatic backup** (`BackupJob`, cron, 14-day retention) plus an
  **on-demand export** (`GET /api/export`). Both build their archive via
  `ExportService`, which uses SQLite's `VACUUM INTO` to get a consistent
  point-in-time snapshot without a mid-write copy.
- Anthropic API key and the free USDA FDC key (`chat-diet.fdc.api-key`, from
  `api.data.gov` — no cost, no expiry) both live in `application.yml`
  (gitignored), not committed. Open Food Facts needs no key.

---

## 12. Status

The phased build plan that originally lived in this section is complete and
superseded by the app as it stands; see the Appendix for what was built, then
later simplified away, versus what was never attempted. This section is kept
short deliberately — treat the rest of this document as the current design,
not a roadmap.

---

## 13. Deferred / not planned

- **Agent registry / orchestrator.** Promoting intent groups to specialist
  agents with their own prompts and scoped toolsets remains the extension
  seam (`IntentDefinition` would gain a `model` field) but hasn't been
  needed — one Haiku loop plus the Opus SQL path covers the current scope.
- **Wearable integration** — Apple Health, Fitbit.
- **General nutrition database browsing beyond what's already been
  logged/cached.** Food Items (§10) lets you search and edit the local
  `FOOD_ITEM` cache, but there's no standalone UI for browsing USDA FDC or
  Open Food Facts data that hasn't already passed through logging.
- **Local LLM** — evaluated and rejected. Modest home-server hardware limits
  practical models to 3–8B, where tool-calling reliability is inconsistent
  enough that the Claude fallback path would fire constantly.

---

## Appendix: what changed since the original design, and why

The original v1 brief planned several subsystems that were either never
built or were built and later removed during a simplification pass. Recorded
here so they aren't rediscovered as new ideas without knowing they were
already tried.

| Area | Original plan | What happened |
|---|---|---|
| Database | H2, rejected SQLite outright (Spring Data JDBC has no built-in SQLite `Dialect`, and the JDBC driver has known Hibernate quirks) | **Adopted SQLite anyway.** A custom `SqliteJdbcDialectProvider` resolved the dialect gap; single-file simplicity and `VACUUM INTO`-based backups won out. `backend/data/h2-archive/` holds the pre-migration H2 files. |
| Photo-based food logging | Vision analysis of a plate photo via an isolated Sonnet subchat, three-tier resolution handling, photo archive + 90-day purge | **Removed entirely.** The only surviving photo feature is barcode-image decode (ZXing, server-side) feeding the existing UPC-logging path — no vision/plate analysis, no archive. |
| Recipes | Per-100g recipe storage, ingredient-quiz intake, a `provisional` flag for rough estimates, fuzzy recipe-name matching | **Never built.** No recipe package, table, or tool exists. Repeat items are handled by the simpler `FOOD_ITEM` cache (UPC or fuzzy name match) instead. |
| Calorie target | Adaptive TDEE: Mifflin-St Jeor BMR, activity multiplier from logged exercise, weight-feedback correction loop adjusting effective TDEE over time | **Removed as a closed loop; later revived read-only.** `set_calorie_goal` still sets a plain calorie number effective from a date, carried forward until changed — nothing auto-adjusts it. But `get_tdee` (§7) now back-calculates a TDEE estimate from logged intake vs. the smoothed weight trend over a 14-day window and displays it; it never writes to `DAILY_TARGET`. The specific thing that got removed — a self-adjusting loop with no damping, prone to oscillating on sparse weigh-ins — stays rejected. |
| Multi-item entries / cost tracking | `FOOD_ENTRY_ITEM` line items, `PURCHASE_HISTORY`, `cost_usd` on entries and shopping items, a `COST` chart metric | **Never built.** Food logging is one row per utterance; no cost-of-food/groceries field exists anywhere in the schema. (`MODEL_PRICING`, added later, tracks the cost of *running the app* — Claude API usage — an unrelated concept; see §2.) |
| Feature-request capture | A `capture_requirement` intent for the user to log feature ideas about the app itself | **Removed.** Use `save_note` instead — freeform notes cover the same need without a dedicated intent. |
| Deployment | Two-service Docker Compose (backend + nginx-served frontend) | **Simplified to one container.** The Spring Boot backend serves the built frontend directly; HTTPS is handled by Spring itself with a Tailscale cert, no reverse proxy. |
| Shopping list | Built in an early phase: `SHOPPING_ITEM` table, a `manage_shopping` intent with 6 tools (add/list/bulk-add/mark-purchased/revert), a dedicated `shop` chat screen | **Removed.** Once `FOOD_ITEM` gained a full management page (lookup, manual entry, soft-delete/restore), the shopping list was a redundant, less-capable second place to maintain the same product data. The table was renamed to `shopping_item_archived` (kept for historical rows, read/written by nothing) rather than dropped. |

### Still-standing rejected designs

| Rejected | Reason |
|---|---|
| Local LLM (Ollama) | Hardware limits reliability below usefulness. |
| Custom UI screens for every entity | Chat plus the SQL agent covers ad-hoc analytics; the read-only/utility pages that do exist (Daily Foods, Chat History, Micronutrients, Database Schema) are for browsing, not data entry. Food Items is the one deliberate exception — see §10 — needed because there was no other way to fix bad cached nutrition data. |
| Push notifications / ntfy | Leaks to third-party relays; chat reminders suffice. |
| Clothing-weight calibration | Smaller than time-of-day noise; both wash out. |
| Confidence ratings in UI | Clutter. The `source` field carries provenance internally without surfacing a confidence score. |
| Six agents in v1 | Latency and cost stacking; no benefit realized yet. |
