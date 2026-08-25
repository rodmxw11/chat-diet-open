# chat-diet — Current Features (as of 2026-08-25)

## What this app is

A personal diet, weight, and vitals tracker with **no forms, buttons for data entry, or
manual food database browsing** — the entire interface is a single chat window (installable
as a PWA on phone or desktop). You tell it what you ate, weighed, or measured in plain
English (typed or spoken), and an LLM (Claude) parses the message, estimates/looks up
nutrition data, and calls the appropriate logging tool. A few auxiliary full-screen pages
(shopping list, notes, daily food log) exist for browsing, but nothing is ever logged
through them — logging only happens via chat.

Single-user, self-hosted (Docker or local JVM), backed by SQLite.

---

## Logging (all via natural-language chat)

- **Food**: describe what you ate in free text ("i ate a medium banana", "2 slices of pizza
  and a coke"); the model estimates calories, protein, carbs, and fat itself. Items under 10
  calories (black tea, water) are acknowledged but not logged as a row.
- **UPC barcode lookup**: give a barcode number and it's looked up against the Open Food
  Facts database for real nutrition data (not an estimate), then cached locally as a reusable
  "food item" so a repeat purchase can be logged by name alone next time ("I had another
  Coca-Cola") without re-scanning.
- **Weight** (lbs), **blood pressure/heart rate**, **exercise** (name + duration, with
  calories-burned estimated if not stated), and **digestive events** (reflux, diarrhea, etc.,
  with optional notes) — all logged the same conversational way.
- **Backdating**: mentioning a past day and/or meal name ("I had a bagel for breakfast
  yesterday") resolves to the correct timestamp automatically, using sensible default clock
  times per meal name (breakfast/lunch/dinner) unless an explicit time is given.
- **Corrections**: a linguistically-marked correction ("make that two slices", "the scale
  actually said 181", "the correct BP is 156 over 65") updates the most recent matching entry
  in place rather than creating a duplicate row; a bare new number with no correction
  language is always treated as a new entry. The prior value is retained for audit.
- **Freeform notes-to-self**: anything you want written down that isn't food/weight/vitals/
  exercise (including feature requests about the app itself) gets timestamped and saved,
  browsable later on a dedicated Notes page.

## Goals & targets

- **Manual daily calorie goal**, settable/changeable any time ("set my calorie target to
  1800", optionally with a future effective date) — carries forward until superseded by a
  later goal. *(No automatic BMR/TDEE-based calorie recommendation — the target is always
  user-specified.)*
- **Remaining-calories tracking**: ask "how many calories do I have left today" for an
  instant target/consumed/remaining breakdown.
- **Configured weekly weight-change rate** (e.g. "lose 1 lb/week") drives the weight-trend
  goal line and the weight projection tool, independent of the calorie target.

## Analytics & Q&A (all conversational)

- **"What did I eat on [day]"** — today, yesterday, or any named/explicit date — answered by
  a dedicated deterministic lookup (not model guesswork), listing each item with time and
  calories plus a running total.
- **Fasting duration** — "how long since I last ate."
- **Weight projection** — "when will I hit 190 lbs" or "what will I weigh by [date]," given
  either a goal weight or a goal date (not both), reported in both pounds and calorie
  surplus/deficit terms.
- **Open-ended natural-language-to-SQL analytics** for anything the above don't cover —
  trends, rankings, "how many times have I logged X," "what's my most common breakfast," etc.
  The model composes and runs a read-only SQL query against your own data, renders it as a
  table inline in chat, and offers a CSV download. Frequently-reused query shapes get saved
  and matched against future similar questions instead of recomposing SQL each time.
- **Reliability engineering behind logging**: because the model occasionally narrates a
  "Logged: ..." confirmation without actually invoking the save (an LLM tool-calling
  reliability issue), the backend independently verifies every food-log claim against
  whether a database write actually happened, automatically retries once with a corrective
  prompt if not, and — if it still can't confirm — tells you plainly that it didn't save
  rather than showing a false confirmation.

## Charts & visualization

- **Calories & macros bar chart** (sidebar on desktop, bottom sheet on mobile): 7-day view
  with per-macro-in-grams segment labels and a total-calories badge per day; 30-day view
  without labels. Reads from a maintained per-day cache, not recomputed from scratch each
  time.
- **Weight trend chart**: actual weigh-ins plus an exponentially-smoothed trend line matching
  the [TrendWeight](https://trendweight.com/math) algorithm — smoothing runs over your entire
  weigh-in history (not just the visible window) with linear interpolation across gaps
  between weigh-ins, so the trend eases across missing days instead of jumping or flat-lining.
  Includes a straight-line goal trajectory overlay and a numeric weigh-in/trend/variance
  table.
- **Ad-hoc charts via chat**: "show me my calories this week," "chart my weight this month,"
  "plot my deficit over the last 3 months" — the model resolves the time frame and
  granularity itself (hour/day/week/month) and can overlay the goal line where relevant.
  Supports calories, macros, weight, and deficit as metrics.

## Browsable pages (view-only; nothing is edited or logged from these)

- **Daily Foods**: date picker (prev/next-day buttons + native date input, defaults to
  today) over a table of that day's food entries — time, food description, calories, and
  macros in "24C 5F 10P" format — with a totals line at the top summing calories and macros
  for the selected day.
- **Notes**: chronological list of every saved note.
- **Shopping list**: dedicated full-screen mode for grocery trips — add items via chat
  (auto-suggests the store based on your prior purchase history for that item), check off
  purchases, undo an accidental purchase mark, and bulk-add items via an interactive picker
  populated from foods you've logged before ("add stuff from my usual items").

## Voice & accessibility

- **Speech-to-text**: a mic button transcribes spoken input into the message box (browser
  Web Speech API).
- **Text-to-speech**: optional toggle to have replies read aloud.

## Offline & multi-device

- **Installable PWA** with offline support: messages composed while offline queue locally
  (IndexedDB) and auto-send once connectivity returns, with a visible queue panel (view/
  delete pending messages, manual retry) and a connectivity status indicator.
- **Conversation is scoped to the calendar day (a "metabolic day," see below), not a device
  or browser tab** — open the app on your phone and your laptop and both see and continue the
  same conversation and food log for the day.
- **"Metabolic day" concept**: the day boundary is a configurable hour (default 4am), not
  literal midnight, so a late-night snack after midnight is still attributed to the day it
  belongs to rather than starting a fresh, empty "today."

## Data ownership & backup

- **On-demand full export**: download a complete zip archive of your database at any time.
- **Automatic nightly backups** with 14-day retention, no manual action required.
- All data lives in a local SQLite file you fully control — no cloud account, no third-party
  data sharing.

---

## Known gaps (useful context for a feature comparison)

- **No camera/photo barcode scanning in the UI.** A backend endpoint to decode a barcode
  from an uploaded photo exists but isn't currently wired to any button — UPC lookup today
  requires typing or speaking the barcode digits.
- **No AI food-photo recognition** (i.e. "log it from a picture of my plate") — this existed
  in an earlier version of the app and was removed during a simplification pass.
- **No automatic BMR/TDEE-based calorie target suggestion** — the daily calorie goal is
  always manually entered, never calculated from age/height/weight/activity level.
- **No recipe or meal-template feature** (save a multi-ingredient recipe once, log it by
  name) — also removed during simplification; only single food items and cached UPC/named
  items can be logged.
- **No fitness-tracker/wearable integration** (Fitbit, Apple Health, Garmin, etc.) — exercise
  and weight are logged manually via chat only.
- **No social/community features** — no friends, sharing, leaderboards, or challenges; this
  is single-user by design.
- **No in-app nutrition database browser** — you can't search/browse a food database
  directly; all food lookups happen implicitly through the chat conversation.
- **No barcode/nutrition-label OCR** beyond the UPC-number lookup described above.
