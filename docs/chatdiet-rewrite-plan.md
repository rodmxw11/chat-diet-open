# chat-diet v2 rewrite: trim unused features, add goal tracking, offline mode, responsive UI

## Context

After a two-week trial, the user wants to cut features that went unused (photo logging, recipe tracking, purchase-cost history) and the TDEE-based adaptive calorie target (too complicated to reason about), replacing it with a manually-set calorie goal they adjust themselves as they lose weight. They also want real progress visibility (a 7/30-day macro chart, a Hacker's-Diet-style weight trend chart), a proper shopping-list mode, visible/manageable offline queueing, and a fully responsive layout (the app is currently phone-width-only with no breakpoints).

This lands at the same time as the H2→SQLite migration in progress. The user has decided **not to preserve old data** — the DB is recreated empty — which means the migration and this rewrite can be done together as one clean-slate Liquibase changelog set, avoiding the SQLite `dropColumn`/`addNotNullConstraint` gaps discovered earlier (Liquibase 5.0.3 has no SQLite generator for either; `dropColumn` silently no-ops instead of erroring, which is worse). One branch, done end-to-end before review (user's choice).

There is also a real, unfinished bug from the SQLite migration: reading `DATE`/`DATETIME` columns back through Spring Data JDBC sometimes yields a `Long` (epoch millis, confirmed via debug output) and sometimes a plain ISO `String`, depending on query shape — not data corruption, but an unfixed driver/ORM inconsistency that will break in production regardless of this feature work. Fixing it permanently is step zero.

## 1. Fix the date-conversion bug (do first, blocks everything)

`backend/src/main/java/com/chatdiet/config/JdbcDialectConfig.java` currently has debug-print converters (`Long->LocalDate`, `Long->LocalDateTime`, treating the Long as epoch millis in the system default zone). Replace with clean, permanent versions (no `println`), and add the missing direct converters for the other observed case:
- `StringToLocalDateConverter` (`LocalDate.parse`)
- `StringToLocalDateTimeConverter` (try `LocalDateTime.parse`; fall back to `LocalDate.parse(value).atStartOfDay()` for a bare date string — this is the exact failure seen in `PhotoPurgeJobTest`: `NumberFormatException` from Spring's `String→Number→LocalDateTime` chain attempt)

Register all four via `JdbcCustomConversions.of(jdbcDialect, List.of(...))` (already the pattern in place — **not** `new JdbcCustomConversions(...)`, which drops Spring Boot's required Jsr310 defaults and breaks the whole context, as already discovered).

Add a small regression test (`JdbcCustomConversionsTest` or similar) that round-trips a `LocalDate`/`LocalDateTime` through a real SQLite `@TempDir` datasource and asserts correct values — this bug was silent and inconsistent once already; a test prevents it recurring unnoticed.

## 2. Liquibase: clean-slate schema rewrite

Delete all 18 current changelog files and replace with a fresh set matching the final schema directly (no ALTER-TABLE-style migrations — every table via `createTable` with `constraints` inline, sidestepping the SQLite gaps entirely). Keep `type: INTEGER` + `autoIncrement: true` for every id column (required for SQLite `AUTOINCREMENT`).

New file set, `backend/src/main/resources/db/changelog/`:
- `001-create-note-table.yaml` — unchanged.
- `002-create-food-entry-table.yaml` — drop `cost_usd`; keep `prep_minutes` (unpopulated today, but not a cost column, out of scope).
- `003-create-weight-entry-table.yaml`, `004-create-vitals-entry-table.yaml`, `005-create-exercise-entry-table.yaml`, `006-create-digestive-event-table.yaml` — unchanged.
- `007-create-daily-target-table.yaml` — simplified to `(id, target_date DATE NOT NULL UNIQUE, target_calories INT NOT NULL)`; drop `effective_tdee`/`weight_lbs_used`.
- `008-create-food-item-table.yaml` — unchanged.
- `009-create-shopping-item-table.yaml` — drop `estimated_cost_usd`.
- `010-create-saved-query-table.yaml` — unchanged.
- `011-create-chat-message-table.yaml` — final shape directly: `(id, metabolic_date DATE NOT NULL, role VARCHAR(16) NOT NULL, content CLOB NOT NULL, created_at DATETIME NOT NULL)` + the `(metabolic_date, created_at)` index. No `chat_session` table.

Fully removed, no replacement: `requirement_entry`, `photo`, `recipe`, `recipe_ingredient`, `purchase_history`.

`db.changelog-master.yaml` includes 001–011 in order. Operationally: delete `backend/data/chat-diet.db` (and `-wal`/`-shm` if present) before first boot so Liquibase's own history table starts clean too — this is a deploy step, not a changeset.

## 3. Backend feature removal

**Photo** (requirement: remove entirely) — delete package `com.chatdiet.photo` (`Photo`, `PhotoRepository`, `PhotoArchiveService`, `PhotoResizeService`, `PhotoAnalysisService`, `PhotoContext`, `PhotoPurgeJob`), `AnalyzeFoodPhotoTool`, `LogFoodFromPhotoTool`, and their tests. Remove `ChatController.chatWithPhoto` (the multipart handler) — `POST /api/chat` becomes JSON-only. Remove photo tool references from `log_food`'s `toolNames`/`promptFragment` in `intents.yaml`. Leave `BarcodeController`/`BarcodeDecodeService` untouched — unrelated (barcode-photo decoding for UPC lookup, not food photos).

**Recipe** (requirement: remove entirely) — delete package `com.chatdiet.recipe` (6 tools, `Recipe`, `RecipeIngredient`, repos) and tests. Delete the `recipe_intake` intent. Remove `find_recipe`/`log_recipe` from `log_food`'s `toolNames`/`promptFragment`.

**Requirement/note merge** — delete `requirement_entry` (entity, repo, `SaveRequirementTool`) and the `capture_requirement` intent. `note`/`SaveNoteTool` already satisfy "append-only, never delete" with zero changes. Broaden `save_note`'s prompt fragment to mention app feedback/requests, since `capture_requirement` is gone.

**Cost tracking / purchase history** (requirement: remove entirely) — delete `com.chatdiet.shopping.PurchaseHistory`/`PurchaseHistoryRepository`. Remove `ChartMetric.COST` and `ChartService.sumCost`/its `purchaseHistoryRepository` dependency. Drop `ShoppingItem.estimatedCostUsd`; change `purchased(store, costUsd)` → `purchased(store)`. `AddShoppingItemTool`'s store-suggestion logic moves from `purchase_history` lookups to a new query directly on `shopping_item` history (`findMostCommonStoreByFoodItemId`/`ByDescription` on `ShoppingItemRepository`). `MarkShoppingItemPurchasedTool` drops `costUsd` and the purchase-history insert.

**TDEE / adaptive target** (requirement: replace with manual goal) — delete `AdaptiveTargetService` and its test, `GetDailyTargetTool` (rewritten below). Trim `NutritionService` down to just `weeklyRateLbs()` (drop `bmr()`, `ageYears()`, `dailyGoalDeltaCalories()`, sex/birthDate/heightIn — `weeklyRateLbs` is the only field anything still needs, via `ProjectionService`, which stays untouched: it's an independent linear-projection tool, not TDEE, not in scope). Remove `chat-diet.profile.*` config from `application.yml`/`.example`; keep `chat-diet.goal.weekly-rate-lbs`.

## 4. New/changed goal + food tracking

- `DailyTarget` record simplified to `(Long id, LocalDate targetDate, Integer targetCalories)`.
- `DailyTargetRepository`: keep `findByTargetDate`; replace `findMostRecent()` with `findMostRecentOnOrBefore(LocalDate date)` (carry-forward semantics — a goal applies from the date it's set until superseded).
- New `GoalService` (`com.chatdiet.nutrition`): `setGoal(LocalDate effectiveFrom, int targetCalories)` (upsert), `targetFor(LocalDate date)`.
- New `SetCalorieGoalTool` (`set_calorie_goal`, new `manage_goal` intent) — request `(Integer targetCalories, LocalDate effectiveFrom)`, `effectiveFrom` defaults to today.
- `GetDailyTargetTool` rewritten against `GoalService` (same tool name/intent `query_data`, same response shape, no TDEE line).
- `ChartService`: swap `adaptiveTargetService.getOrComputeTarget(date)` for `goalService.targetFor(date)`.
- New `GET /api/summary/today` (new `SummaryController`, package `com.chatdiet.summary`) → `{metabolicDate, targetCalories, consumedCalories, remainingCalories, entryCount}` (null target/remaining if no goal ever set; `entryCount` = today's `food_entry` row count) — this is what the header polls; nothing today serves this without going through chat. `entryCount` was added per the design handoff's header sub-caption ("412 eaten · 3 entries").

**Food-item shopping-list picker** (new "food item shopping list" chat command):
- New `ListFoodItemsForShoppingTool` (`list_food_items_for_shopping`, intent `manage_shopping`) — returns `food_item` rows ordered by `use_count DESC, name`.
- New `AddShoppingItemsBulkTool` (`add_shopping_items_bulk`, request `List<Long> foodItemIds`) — the picker's "confirm" round-trips through chat as synthesized natural-language text (e.g. "Add these to my shopping list: ..."), staying consistent with the existing model-drives-all-writes architecture rather than adding a parallel non-chat mutation path.
- `ChatResponse` gains a fourth optional field, following the existing `chartSeries`/`sqlAnswer` pattern: `List<FoodItemOption> foodItemOptions` (new record `FoodItemOption(Long id, String name, Double typicalServingG)`), populated via a new request-scoped `FoodItemPickerContext` (mirrors `ChartResultContext`), read by `ChatController`. Update `openapi.yaml`'s `ChatResponse` schema.

## 5. Shopping mode

- New `ShoppingItem.pending()` — reverts `PURCHASED` back to `PENDING` (the missing "uncheck" operation).
- New `RevertShoppingItemTool` (`revert_shopping_item_purchased`, intent `manage_shopping`) for the chat path — a dedicated tool rather than overloading the existing mark-purchased tool as a toggle, so the model can't flip the wrong direction.
- New `ShoppingListController` (package `com.chatdiet.shopping`) for the dedicated in-store checklist screen (this needs fast toggle without a full chat round-trip): `GET /api/shopping-items` (pending first, each group chronological), `POST /api/shopping-items/{id}/toggle` (uses the same `purchased()`/`pending()` entity methods — safe as a plain toggle here since the frontend always knows current state).
- Update `manage_shopping`'s `promptFragment` in `intents.yaml`: add the new tools, drop all cost/store-history language.

## 6. New chart data endpoints

Both new charts have a different shape than `ChartService`'s existing per-metric line-series pattern (stacked macros; scatter+trend+goal-line), so they get purpose-built read models rather than being forced into `ChartSeries`. `show_chart`/`ChartService` stay exactly as-is for existing inline chat charts (minus the `COST` metric).

- New package `com.chatdiet.dashboard`:
  - `MacroChartService.dailyMacros(from, to)` → `List<DailyMacros(LocalDate date, double proteinG, double carbsG, double fatG, int calories)>`, built from `FoodEntryRepository` bucketed by metabolic day via the existing `DayBoundaryService` (reuse `ChartService`'s bucketing approach).
  - `WeightTrendService.trend30Day()` → `WeightTrendResponse(List<WeighIn> actual, List<TrendPoint> smoothed, GoalLine goal)`. Trend = classic Hacker's Diet exponential smoothing (`trend[i] = trend[i-1] + (actual[i] - trend[i-1]) * 0.1` on days with a weigh-in; flat carry-forward on days without). `GoalLine` = a straight line from the window's first weight, extrapolated at `nutritionService.weeklyRateLbs()/7` per day — reuses the existing config value directly, no changes needed to `ProjectionService` (confirmed it's a separate, self-contained linear-projection tool not tied to TDEE).
- New `DashboardController`: `GET /api/dashboard/macros?days=7|30`, `GET /api/dashboard/weight-trend`.
- New `GET /api/ping` (trivial, no DB touch) for the frontend connectivity heartbeat.
- Update `openapi.yaml` for all new endpoints (`/api/summary/today`, `/api/shopping-items`(+toggle), `/api/dashboard/macros`, `/api/dashboard/weight-trend`, `/api/ping`) and remove the multipart-photo variant of `POST /api/chat`.

## 7. Frontend: offline mode

Replace the current Workbox `backgroundSync` runtime-caching (`vite.config.ts`) entirely — it's opaque, can't be listed/deleted from, which the requirements need. Keep `VitePWA` for manifest/installability only.

- New `src/lib/offlineQueue.ts` — IndexedDB (`chat-diet-queue` DB, `queued-requests` store, keyPath `id`), storing `{id, text, clientSentAt, createdAt}`. `drainQueue()` replays queued requests **in order** on reconnect (order matters — `ChatController.replyAt` depends on `clientSentAt` ordering), stopping on first failure so the rest stay queued.
- New `src/store/connectivitySlice.ts` — state machine `online | reconnecting | offline`:
  - Poll `GET /api/ping` once/minute. Success → `online` (+drain if transitioning from reconnecting/offline). Failure while `online` → `reconnecting`, start a 30-minute deadline. Failure past the deadline → `offline`, **stop** auto-polling (per spec). While `offline`, a manual "try reconnect" action does one ping; success reconnects, failure stays red until tried again.
  - Also listen to `window.online`/`offline` events as a fast-path hint to ping immediately, not a replacement for the polling contract.
- `chatSlice.ts`'s `sendMessage` now goes through the queue-aware send path instead of relying on the `TypeError`-heuristic against Workbox's silent rejection.
- New `src/components/StatusLight.tsx` (green/yellow/red in the header, popover with "try reconnect" when red) and `src/components/OfflineQueuePanel.tsx` (list + per-item delete of queued requests).

## 8. Frontend: new charts, shopping mode, food picker

- New `src/components/charts/MacroBarChart.tsx` — Recharts stacked `BarChart` (protein/fat/carbs, red/yellow/green per the reference image), calorie badge per day, no tooltip/legend (not interactive per spec).
- New `src/components/charts/WeightTrendChart.tsx` — Recharts `ComposedChart`: `Scatter` (actual weigh-ins, diamond shape), `Line` (smoothed trend), dashed `Line`/`ReferenceLine` (goal trajectory), plus a plain `<table>` below reusing the existing `.sql-table` CSS. Not interactive — no tooltip/click handlers.
- Both are pure `{data} -> JSX`, fed by a new `dashboardSlice.ts` (fetches `/api/dashboard/macros`, `/api/dashboard/weight-trend`) — deliberately so the *same* components render inside both the mobile modal and the desktop sidebar without duplicating chart logic or re-fetching on breakpoint crossing.
- New `src/components/shopping/ShoppingModeView.tsx` + `ShoppingItemRow.tsx` + `src/store/shoppingSlice.ts` (fetches `/api/shopping-items`). Google-Keep behavior: toggling an item flips its status locally, the array is re-sorted (pending group first, purchased group at the bottom, each chronological), and `POST /api/shopping-items/{id}/toggle` fires in the background to reconcile. CSS: `.shopping-item.purchased { opacity:.5; text-decoration:line-through }`, plain `transition` + stable React keys for a smooth reflow (no animation library needed — none exists today and the requirement doesn't call for anything fancier).
- New `src/components/shopping/FoodItemPicker.tsx` — renders `ChatMessage.foodItemOptions` inline (same pattern as `ChartRenderer`/`SqlResultTable` today): checkbox list, "Add N to shopping list" button, confirm synthesizes chat text (see §4). Per the design handoff, its confirm button navigates straight into the shopping-list screen (§10) rather than just closing.
- **Superseded by the design handoff (§10): shopping mode is its own full-page view, not a modal/sheet.** Implemented as a plain `screen: 'chat' | 'shop'` piece of state (no router needed — still just a component swap, matching the "no router for one screen" reasoning, just resolved as a full-screen swap instead of an overlay).

## 9. Frontend: modal infrastructure + responsive rewrite

- New `src/components/modal/Modal.tsx` wrapping the native `<dialog>` element (`showModal()`/`close()`) — free focus-trap, Esc-to-close, top-layer stacking, zero new dependency. `variant: 'modal' | 'sheet'`, with a CSS media query forcing bottom-sheet presentation under the mobile breakpoint regardless of the prop, so callers don't branch.
- New `src/components/layout/AppShell.tsx` — CSS Grid wrapper around the existing (working) flex-column chat skeleton (header/chat/input already scroll correctly via `flex:1`/`overflow-y:auto` — the actual gap is no responsive behavior at all beyond a fixed 640px cap, and nowhere for a sidebar to live).
  ```css
  .app-shell { display:grid; grid-template-columns:1fr; grid-template-rows:auto 1fr auto; height:100dvh; overflow:hidden; }
  @media (min-width: 1080px) {
    .app-shell { grid-template-columns: minmax(0,660px) 420px; justify-content:center; column-gap:24px; max-width:1440px; margin:0 auto; }
    .app-shell .sidebar { grid-column:2; grid-row:1/span 3; overflow-y:hidden; } /* per handoff: side panel doesn't scroll internally, content sized to fit */
  }
  ```
  **Superseded by the design handoff (§10): breakpoints and dimensions are now fixed, not a placeholder to tune.** Phone <620px, tablet 620–1079px, desktop ≥1080px; chat column caps at 660px, sidebar is a fixed 420px, whole shell caps at 1440px centered. (Resolves the earlier "validate 1024px, adjust if cramped" open item from the requirements doc — the design handoff already did that validation.)
- `Sidebar.tsx` (desktop only, ≥1080px) renders `MacroBarChart`/`WeightTrendChart` unconditionally, permanently visible, stacked — same components the phone/tablet modal path uses. On phone/tablet, per the handoff, the two charts are **two separate single-chart bottom sheets**, not one combined sheet — each opens independently from the header menu, sized to `90dvh` max with internal scroll if content overflows.
- CSS audit: no `background-attachment:fixed` exists today (confirmed, no background-image usage at all) — nothing to migrate, but worth confirming again post-rewrite. Add `env(safe-area-inset-*)` padding to header/input. Bump touch targets to 44px minimum (icon toggles, shopping checkboxes). Ensure all form inputs stay 16px (already true for the one existing input; carry forward to new ones).
- Remove photo UI: camera button + hidden file input from `Header.tsx`, `sendPhoto` thunk + `imageUrl` from `chatSlice.ts`, `.message-photo` CSS, the photo render branch in `ChatWindow.tsx`.
- Header additions: `StatusLight`, goal/remaining-calories display (new `summarySlice.ts` polling `/api/summary/today`), menu entries for the two charts and shopping mode.

## 10. Design handoff (Claude Design)

A high-fidelity design reference arrived at `docs/design-handoff/design_handoff_chatdiet_pwa/` (`README.md` + `ChatDiet.dc.html`, a self-contained HTML prototype — reference only, not to be ported directly). Per its README, colors/typography/spacing/component states are final and should be implemented pixel-accurately. Where it conflicts with earlier judgment calls in this plan, the design handoff wins (marked inline above as "superseded"). Key specifics not already covered above:

- **Design tokens** (add as CSS custom properties in `index.css`, replacing/extending the current light-mode token set — dark-mode equivalents aren't specified by the handoff and will need deriving separately, consistent with the existing `prefers-color-scheme` pattern): page bg `#f6f2fb`; header/input bg `#faf8fd`; surfaces `#ffffff` with tints `#f2ecfa`/`#f5f0fa`; borders `#e5dcf3`/`#e2d7f0`/`#e9e2f4`/`#eee8f8`; text `#2b2140` (primary), `#372c52`/`#756a8f`/`#9086a8` (secondary tiers); accent purple `#6b4fae` (hover `#5a3f9c`), accent tint `#ece3f9`; macro colors protein `#ef6b52`, fat `#f2c14e`, carbs `#4fb987`; status online `#4fb987`, retrying `#c99a2a`, offline `#d1533e`; destructive text `#c1503a`/border `#ecb9ac`/hover bg `#fbeceb`. Radius scale 6–9px (controls), 12–14px (bubbles/cards), 16–18px (sheets/menus).
- **Typography**: IBM Plex Sans (UI) + IBM Plex Mono (all numeric/meta/data — calorie counts, timestamps, table content, labels). The handoff's own HTML pulls these from Google Fonts' CDN — **do not do this in the actual app**: it's a PWA required to work offline, and an external font CDN is an unreliable/unavailable dependency once offline. Self-host the woff2 files under `public/fonts/` instead and add them to the PWA precache list in `vite.config.ts`.
- **Explicit client state model** given by the handoff, worth adopting close to verbatim rather than re-deriving slice shapes from scratch: `screen: 'chat' | 'shop'`, `overlay: null | 'chartsMacros' | 'chartsWeight' | 'queue'` (independent of `screen`), `menu: boolean` (header dropdown), `connection: 'online' | 'retrying' | 'offline'`, `range: 7 | 30` (macro chart window), plus per-row checkbox state for the food picker/shopping list and a `{id, text, when}[]` queue array. This maps directly onto `connectivitySlice`/`dashboardSlice`/`shoppingSlice`/a small UI slice from §7–§9 above — no new architecture needed, just naming/shape alignment.
- **Header content**, more specific than §9's earlier description: mono "chatdiet" wordmark, goal headline ("1,588 left of 2,000" or "No goal set yet" + a prompt), a mono sub-line ("412 eaten · 3 entries" — needs the new `entryCount` field, §4), a connectivity dot (9px, pulses via opacity keyframe while retrying) with a text label, and the menu button — dropdown rows are "Calories & macros / Weight trend / Shopping mode / Waiting to send", each with a right-aligned mono hint/count.
- **Offline queue panel**: bottom sheet on phone/tablet, centered modal on desktop (i.e. it's the one surface that stays an overlay at every width, unlike shopping mode) — confirms §7/§9's `Modal` component design as-is; each row shows a relative "queued N min ago" caption and a delete action; empty state "Nothing queued."; footer "try to reconnect now" action, prominent when offline.

## Build order

1. Date-conversion fix + test (§1) — independent, do first.
2. Liquibase rewrite (§2) against a deleted local `chat-diet.db`.
3. Backend removals (§3) — safe first since nothing new depends on the old code.
4. Backend field/entity trims (`FoodEntry`, `ShoppingItem`, `DailyTarget`, `NutritionService`) and all call sites.
5. New backend services/tools/controllers (§4–§6); update `intents.yaml` and `openapi.yaml` fully.
6. `./gradlew test` green before touching frontend — frontend work assumes stable REST contracts.
7. Frontend: remove photo UI + Workbox config → offline queue/status-light (§7, self-contained) → `Modal` → `AppShell`/grid (§9) → charts + `dashboardSlice` (§8) → shopping mode (§8) → food picker (§8) → header wiring.
8. CSS breakpoint pass last, tuned against real rendered content rather than placeholders.

## Verification

**Backend**: `./gradlew test` fully green, including the new date-conversion regression test. Delete `data/chat-diet.db`, boot fresh, confirm all 11 changelogs apply cleanly with no SQLite generator warnings. Manually exercise each changed/new chat tool (log a meal — confirm no `cost_usd` error; `set_calorie_goal`; `get_daily_target`; add/list/mark-purchased/revert a shopping item; `list_food_items_for_shopping` → confirm `ChatResponse.foodItemOptions` populated; `show_chart` MACROS). Curl each new REST endpoint (`/api/ping`, `/api/summary/today`, `/api/shopping-items`+toggle, `/api/dashboard/macros`, `/api/dashboard/weight-trend`).

**Frontend**: `npm run build` (TS will catch stale references to removed photo/cost fields), `oxlint` clean. Manual browser check at 360/390/430/768/1024/1440px — no horizontal scroll, header/input stay fixed while chat scrolls, sidebar appears at the 1080px breakpoint per the design handoff, modals become bottom sheets on mobile, 44px touch targets. Manual offline test via devtools network throttling: send while offline → appears in `OfflineQueuePanel`, not lost; status light green→yellow within a minute, stays yellow (not red) before 30 minutes; reconnect → auto-drain fires in order, replies appear; delete a queued item → confirm it never sends. Manual shopping-mode check: toggle grays out + drops to bottom with smooth transition, untoggle restores it, no cost fields anywhere. Manual food-item-picker round trip.

## Open items flagged during planning (judgment calls, not user-decided)

- ~~Sidebar breakpoint~~ / ~~shopping mode as modal vs. route~~ — both resolved by the design handoff (§10): fixed breakpoints (1080px), shopping mode is a full-page screen swap.
- Chart chat-keyword triggers treated as optional/secondary — the button/menu path is primary and simplest to get right first; the requirement only needs one of button/menu/keyword.
- No frontend test framework exists today (no Vitest/RTL) — not introduced here since it wasn't asked for; flagging in case you want it added as a separate follow-up.
- Dark-mode token equivalents for the new design's palette aren't specified by the handoff (it's a light-mode-only reference) — will need deriving to stay consistent with the app's existing `prefers-color-scheme` support; flag for a look-over once implemented rather than guessing values now.