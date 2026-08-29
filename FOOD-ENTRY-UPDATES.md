# FOOD-ENTRY-UPDATES.md — Food lookup, gram logging, item management

Companion spec to `SPEC.md`. Covers a set of related changes to food entry
that came out of starting a gram-scale logging habit and working with a
nutritionist: better ground-truth data for raw/generic foods, a real record
of the weighed amount, a way to manage the `FOOD_ITEM` cache directly, and
retiring the shopping-list feature until it's earned its keep.

Written as a diff against `SPEC.md`. Where this document is silent, `SPEC.md`
still governs.

---

## 1. Problem statement

Three separate but related gaps, discovered while shifting to gram-weighed
logging:

1. **Free-text estimation is the only path for raw/generic foods.** Open
   Food Facts only covers packaged, barcoded products. Produce, meat, and
   other unbranded foods fall straight to a model estimate, which is the
   least accurate tier the app has (§1.5 of `SPEC.md`) — and now that
   quantity is precise (a scale reading), the per-100g reference is the only
   remaining source of error. Precision on one side of the multiplication
   and a guess on the other is a wasted scale.
2. **The gram amount itself isn't persisted.** `FOOD_ENTRY` stores only
   derived totals. Once logging is gram-based, the raw weighed amount is the
   one precise, correctable input variable — it should be a first-class
   column, not implied backwards from a total.
3. **No way to manage the `FOOD_ITEM` cache directly.** Bad or partial data
   (an OFF entry missing sodium, a stale model estimate) can only be fixed by
   re-triggering a lookup, if at all. There's no CRUD surface.

Shopping list removal is bundled into this doc because it's shipping in the
same pass, not because it's related to the above.

---

## 2. Data model changes

### `FOOD_ENTRY` — new columns

| Column | Type | Notes |
|---|---|---|
| `food_item_id` | long, FK → `FOOD_ITEM.id`, nullable | Was implied by the ERD relationship in `SPEC.md` (`FOOD_ENTRY }o--|| FOOD_ITEM`) but missing from the field list. Added now because gram-based logging routes through the cache far more often than free-text logging did. Null for entries with no cache hit (fully free-text, no matching `FOOD_ITEM`). |
| `amount_grams` | real, nullable | The weighed input. Null for pre-this-feature entries and for any entry logged without a scale (eating out, etc.) — not backfilled. |

### `FOOD_ITEM` — new columns

| Column | Type | Notes |
|---|---|---|
| `deleted_at` | datetime, nullable | Soft delete. Same pattern as `corrected_at` elsewhere in the schema — a timestamp, not a boolean, so "when" is free. |

`lookup_source` gains a new value in practice: `FDC`, alongside the existing
`MANUAL` / `UPC` / OFF-derived strings. Still a free-text field, no schema
change required — `SPEC.md` §3 already specifies this column as open-ended.

### `SHOPPING_ITEM` — archived, not dropped

```xml
<changeSet id="archive-shopping-item" author="rod">
    <renameTable oldTableName="shopping_item" newTableName="shopping_item_archived"/>
</changeSet>
```

Table renamed, not dropped. Data and the `food_item_id` FK stay intact. No
code references the table going forward. Remains visible in the SQL agent's
schema DDL prompt (§8 of `SPEC.md`) so ad-hoc queries against old shopping
history still work — the point of the SQL agent is querying your own data
regardless of which UI features are currently active.

If the feature is reinstated later, decide then whether old rows are still
meaningful or whether to start a fresh table and leave the archive alone.

---

## 3. Food lookup: three-tier resolution

Replaces the single-step `NameMatch --> Estimate` transition in `SPEC.md`
§6's state diagram with a tiered lookup, applied before falling back to a
fresh model estimate:

```
NameMatch
  --> FOOD_ITEM cache (fuzzy match, any lookup_source, deleted_at IS NULL)
        --> Scale --> Persist
  --> FDC lookup (Foundation Foods / SR Legacy — raw/generic foods)
        --> cache as FOOD_ITEM (lookup_source = FDC) --> Scale --> Persist
  --> model estimate (no cache hit, no FDC match — prepared/homemade/mixed
      dishes)
        --> cache as FOOD_ITEM (lookup_source = MODEL_ESTIMATE) --> Persist
```

Every distinct food gets estimated at most once; every subsequent mention
hits the cache and scales deterministically, same shape as the existing
OFF/UPC path. This also fixes a consistency problem the single-step
estimation had: two independent "5 celery sticks" utterances on different
days previously could get two different LLM estimates. Caching the first
estimate makes every later mention deterministic.

### USDA FoodData Central integration

- New external client, `FdcClient`, alongside the existing
  `OpenFoodFactsClient`.
- Auth: free `api.data.gov` API key, passed as a query parameter (not a
  header).
- Rate limit: 1,000 requests/hour per IP (per api.data.gov's shared key
  system) — generous for single-user volume, no special handling needed
  beyond surfacing a clear error if it's ever hit.
- Data type filter: restrict searches to `Foundation Foods` and
  `SR Legacy` — these are the raw/generic, analytically-sourced sub-databases.
  Skip `Branded Foods` (that's OFF/UPC's job) and `Survey (FNDDS)` /
  `Experimental Foods` (not relevant here).
- Values come per 100g or per portion object; scaling to `amount_grams`
  reuses the exact same per-100g × grams/100 math already built for OFF —
  no new arithmetic pattern.
- Raw nutrient IDs (USDA nutrient numbers, e.g. 1008 = energy/kcal, 1003 =
  protein) require a mapping table to the app's calorie/protein/carbs/fat/
  micronutrient fields. This mapping is new work — FDC does not return
  named fields the way OFF's product JSON does.
- Licensing: CC0 public domain. No attribution requirement (unlike OFF's
  ODbL), though citing FoodData Central as source is requested.

### Nutrition-label photo OCR (fallback only)

Not a parallel always-available input mode — a fallback specifically for
`UpcPath`'s `NotFound` branch (OFF has no entry for a store-brand or
regional product).

```
Decode --> Lookup (OFF) --> NotFound
                               --> offer label-photo capture
                                     --> vision extraction (Haiku, strict
                                         JSON schema: field names, units,
                                         nullable gram-equivalent)
                                     --> gram weight present
                                           --> convert to per-100g, cache as
                                               FOOD_ITEM (lookup_source =
                                               LABEL_OCR) --> Scale --> Persist
                                     --> gram weight absent
                                           --> log at serving level, do not
                                               cache as a reusable FOOD_ITEM
                                               (same treatment as the
                                               sub-10-calorie no-cache rule)
```

Treated as structured extraction, not estimation — a US Nutrition Facts
panel is a standardized layout, so this is closer to OCR-then-scale than to
the plate-photo estimation that was previously removed (see `SPEC.md`
Appendix). All arithmetic still happens in Java against the extracted
numbers, never inline by the model.

Every extracted number should be echoed back in chat before being cached
into `FOOD_ITEM` — an OCR misread (digit swap, decimal point) that gets
cached silently corrupts every future reuse of that item.

---

## 4. Gram-based logging

- Utterances are expected to state grams explicitly ("42g of celery"). No
  unit conversion or quantity-estimation parsing needed for this path — the
  model extracts a number and a food name, nothing more.
- `amount_grams` is persisted on `FOOD_ENTRY` (see §2).
- Corrections operate on `amount_grams` directly going forward — "make that
  50g" recomputes from the cached `FOOD_ITEM`'s per-100g values × 50, rather
  than inferring a new total backwards from the old one.
- Editing a `FOOD_ITEM`'s per-100g values (via the CRUD page, §5) is
  prospective only. Historic `FOOD_ENTRY` rows store snapshotted totals at
  log time, not a live reference — correcting an item's data doesn't
  retroactively change past entries.

---

## 5. `FOOD_ITEM` CRUD page

New frontend screen, `foodItems`, added to `uiSlice.ts`'s screen list. Not
date-scoped, so it doesn't reuse the merged date-nav header the other five
screens share — its own list/search header instead.

This is a deliberate, second exception to `SPEC.md` §10's "no form-based
entry anywhere" (the first being none — this is actually the first, since
shopping list, now archived, never had a dedicated form either). Worth
stating explicitly rather than letting it read as an inconsistency later.

### Behavior

- Searchable table: name, source badge, calories/protein/carbs/fat per
  100g, use count. Micronutrients and `typical_serving_g` live in the edit
  modal, not the table — twelve columns isn't readable.
- Active / Deleted tabs. Default view is Active
  (`WHERE deleted_at IS NULL`).
- Edit modal: full nutrient set, source dropdown
  (`OFF` / `FDC` / `MODEL_ESTIMATE` / `MANUAL` / `LABEL_OCR`), required
  fields name + calories.
- Delete is soft: sets `deleted_at`, confirmed via a dialog that states past
  logs and any archived-shopping references still resolve. No
  block-if-referenced logic needed anywhere, since nothing that references a
  `FOOD_ITEM` needs it to still be "active" to resolve.
- Restore action (`deleted_at = null`) available from the Deleted tab.
- Fuzzy-match/cache-lookup queries used elsewhere in the app (chat logging,
  this page's search) filter `deleted_at IS NULL` — a soft-deleted item
  stops surfacing for new matches but doesn't disappear from history.

### REST surface

```
GET    /api/food-items?q=&includeDeleted=
GET    /api/food-items/{id}
POST   /api/food-items
PUT    /api/food-items/{id}
DELETE /api/food-items/{id}        // sets deleted_at
POST   /api/food-items/{id}/restore // clears deleted_at
```

No new chat intent or tool for this — it's a form-only surface, not
chat-driven, consistent with the decision to keep it as a page rather than
adding an eighth log_food-adjacent intent.

---

## 6. Open items

- **`FdcClient` data-type filtering and search-query construction** — free
  text like "yogurt" is ambiguous between a generic Foundation Foods match
  and something that should really go through OFF/UPC. Needs a concrete
  rule, not left to the model to guess per-call.
- **USDA nutrient-ID → app-field mapping table** — not yet written. FDC
  does not return named fields; this is real, non-trivial glue code.
- **Per-field provenance** — a `FOOD_ITEM` row with OFF-sourced macros but a
  manually-entered sodium value (via the CRUD page) still has one
  `lookup_source` string for the whole row. Flagged, not resolved — likely
  not worth a `per_field_source` JSON blob for a single-user app, but
  explicitly deferred rather than accidentally overlooked.
- **Label-OCR endpoint** (`POST /api/nutrition-label/decode`) — scoped here,
  not yet built. Depends on the barcode camera UI work already planned in
  Phase 1 of the broader roadmap, since it reuses the same capture flow.

---

## 7. Appendix addition (for `SPEC.md`)

| Area | What happened |
|---|---|
| Shopping list | Built (`manage_shopping` intent, 6 tools, `SHOPPING_ITEM` table, `shop` screen), then archived pending more usage experience with the app overall — not a "tried and replaced" removal like the others in the main Appendix, and not necessarily permanent. Table renamed to `shopping_item_archived`, not dropped; external tools (Google Keep) cover the interim need. |
