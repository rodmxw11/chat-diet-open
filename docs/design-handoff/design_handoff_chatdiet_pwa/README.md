# Handoff: ChatDiet Responsive PWA

## Overview
A responsive, chat-first UI for ChatDiet, a single-user diet-tracking PWA. The core loop is a text chat where the user logs food/weight in natural language; charts, shopping list, and an offline queue support that loop without competing with it. One layout adapts across phone, tablet, and desktop — not separate designs.

## About the Design Files
The bundled file (`ChatDiet.dc.html`) is a **design reference built in HTML** — a working prototype of look, layout, and interaction states, not production code to copy directly. Recreate this design in the target codebase's existing environment (React, etc. — per `docs/chatdiet-rewrite-plan.md` §9) using its established patterns and libraries, matching this reference pixel-for-pixel where practical.

## Fidelity
**High-fidelity.** Colors, typography, spacing, and component states below are final; implement pixel-accurately.

## Screens / Views

### 1. Chat (home screen)
- **Layout**: Fixed-height shell (`100dvh`, no page scroll). Column: header (fixed) → scrollable message list (flex:1) → input bar (fixed). Message list content is capped at `max-width:660px`, centered.
- **Header**: `56–64px` tall, `padding:14px 16px` (respect `env(safe-area-inset-top)`), background `#faf8fd`, bottom border `1px solid #e5dcf3`. Contents left→right: small mono wordmark "chatdiet" (12px, `#756a8f`, lowercase, tracking .06em) → goal headline+sub (flex, truncates) → connectivity dot button → menu button (≡).
  - Goal headline: 15px/500 `#2b2140` (e.g. "1,588 left of 2,000"); if no goal set, "No goal set yet" + sub prompt "say “set my goal to 1800”".
  - Goal sub: 11px mono `#756a8f` (e.g. "412 eaten · 3 entries").
  - Connectivity dot: 9px circle. online=`#4fb987`, retrying=`#c99a2a` (pulses via 1.2s opacity keyframe), offline=`#d1533e`. Label text next to it, 11px mono. Tapping when offline opens the queue panel; otherwise cycles state for demo only (remove in production — real app drives this from actual connection state).
  - Menu button: 38×38px, white bg, `1px solid #e2d7f0`, radius 9px, "≡" glyph. Opens dropdown (230px wide, white, `1px solid #e5dcf3`, radius 12px, shadow `0 18px 40px rgba(80,50,120,.18)`) with rows: Calories & macros, Weight trend, Shopping mode, Waiting to send (each row shows a right-aligned mono hint/count).
- **Message list**: vertical stack, `gap:16px`, `padding:20px 16px 8px`, scrolls independently.
  - Assistant bubble: `background:#ffffff`, `border:1px solid #e5dcf3`, radius `14px 14px 14px 4px`, align left, max-width 86–96%, text `#372c52`, 15px/1.5.
  - User bubble: `background:#ece3f9` (purple tint), radius `14px 14px 4px 14px`, align right, max-width 86%, text `#3a2a5c`.
  - Inline data table (assistant bubble variant): header row mono 11.5px `#756a8f` on `#f5f0fa`, body rows mono 12.5px `#3a2f56`, protein values in `#ef6b52`, carb values in `#4fb987`, row border `1px solid #eee8f8`, outer border `1px solid #e9e2f4`, radius 9px.
  - Inline food-item checklist (assistant bubble variant): rows in a `#f5f0fa` panel, each row a full-width button (min-height 44px) with a 19px checkbox (checked = `#6b4fae` fill + white ✓), label, right-aligned mono qty in `#9086a8`. Confirm button below: solid `#6b4fae`, white text, radius 9px, hover `#5a3f9c` — routes to the Shopping page.
  - Queued/offline message: right-aligned, dashed border `1px dashed #cabbe4`, bg `#f2ecfa`, text `#6b6084`, plus a small mono "queued · waiting for network" caption in `#c99a2a` below it — visually distinct from a sent bubble.
- **Input bar**: `border-top:1px solid #e5dcf3`, bg `#faf8fd`, padding `12px 16px` (+ safe-area bottom). Row: growable textarea in a white pill (`border:1px solid #e2d7f0`, radius 13px) with an inline clear "✕" button at its right edge — mic button (46×46, radius 12) — speak/TTS button (46×46) — send button (solid `#6b4fae`, white text, radius 12, hover `#5a3f9c`). Mic/speak active state: bg `#ece3f9`, icon color `#5a3f9c`; inactive: white bg, icon `#756a8f`. Font-size 16px on the textarea (prevents iOS zoom-on-focus). No camera/photo control.

### 2. Calories & macros chart (on-demand)
- Card: bg `#fbf9fd`, `1px solid #e9e2f4`, radius 14px, padding 14px.
- Header row: title 13px/600 `#4a3d68`, and a 7d/30d segmented toggle (pill container `#f2ecfa`, `1px solid #e5dcf3`; active segment bg `#ece3f9` text `#4a3d68`, inactive transparent text `#756a8f`).
- Per-day column (height 250px total): a calorie badge (`#6b4fae` bg, white text, pill radius) above a stacked bar — protein `#ef6b52`, fat `#f2c14e`, carbs `#4fb987`, each stacked height proportional to grams, with in-bar gram+letter labels at 7-day zoom only (30-day view drops in-bar labels — too narrow). Day-of-week label below in mono `#9086a8`.
- Static — no hover, tooltip, zoom, or click targets on the chart itself.

### 3. Weight trend chart (on-demand)
- Card: same container style as above. Title "Weight trend" + "30 days" label.
- SVG line chart: gridlines `#ece4f7`, axis labels mono 9px `#9086a8`; dashed goal line `#c99a2a`; solid trend line `#6b4fae` 2.2px; discrete weigh-in dots `#2b2140` r=2.8. Legend row below: "● weigh-in", "— trend" (purple), "-- goal" (gold).
- Data table beneath: date / weight / trend / variance columns, mono, variance colored `#ef6b52` (gain) or `#4fb987` (loss). Static, no interactivity.

### 4. Shopping list (own page, not a modal)
- Full-page view that replaces the chat screen (not an overlay/sheet) — reached via the menu or the food-checklist confirm button. Header: back arrow (←) + "Shopping" title + mono progress caption ("N of 8 done"), same header chrome as the chat header.
- Rows: min-height 58px, full-width tap targets, 24px checkbox, label, right-aligned mono qty. Checking mutes the row (`color:#a79bc0`, strike-through, checkbox fills `#c9bcdf`) and the row sorts to the bottom of the list — pending items always stay above done items, nothing is removed from view.

### 5. Offline queue panel (on-demand overlay)
- Bottom sheet on phone/tablet (`border-radius:18px 18px 0 0`, slides from bottom), centered modal on desktop (`border-radius:16px`). Header: "Waiting to send" + live connectivity label, close ✕.
- Each queued row: message text, relative "queued N min ago" caption, and a delete button (`1px solid #ecb9ac`, text `#c1503a`, hover bg `#fbeceb`). Empty state: "Nothing queued." Footer: "try to reconnect now" button, shown especially when status is red/offline.

## Interactions & Behavior
- **Charts on phone/tablet are two separate single-chart modals** (not one combined sheet) — "Calories & macros" and "Weight trend" each open their own bottom sheet sized to the phone's width, opened independently from the menu. On desktop both charts render together, permanently, in a side panel — no modal.
- Desktop side panel does not scroll internally (content is sized to fit); the phone/tablet chart sheets do scroll if content exceeds `90dvh`.
- Shopping list opens as its own page (full navigation, back arrow), not a sheet/overlay — this is a deliberate deviation from a typical "on-demand overlay" pattern for this one surface.
- The offline queue is the one panel that stays a dismissable overlay/sheet on all sizes.
- Tapping the connectivity dot in this prototype cycles online → retrying → offline for demonstration; wire it to real network state in production, including the described exponential-backoff reconnect behavior.
- Only the message list (or shop list) scrolls — header and input bar are pinned top/bottom via a fixed-height (`100dvh`) shell with `overflow:hidden` on the page and `overflow-y:auto` only on the inner list.

## State Management
- `screen`: `'chat' | 'shop'` — which full page is showing.
- `overlay`: `null | 'chartsMacros' | 'chartsWeight' | 'queue'` — which on-demand panel is open (mutually exclusive with itself; independent of `screen`).
- `menu`: header dropdown open/closed.
- `connection`: `'online' | 'retrying' | 'offline'` — drives the status dot, dot animation, and whether queued-message styling shows in chat.
- `range`: `7 | 30` — macro chart window.
- Per-row checkbox state for the inline food picker and the shopping list (keyed by item).
- A queue array of `{id, text, when}` for not-yet-sent messages, each removable.

## Design Tokens
- **Page background**: `#f6f2fb`
- **Header/input bar background**: `#faf8fd`
- **Surface (cards, bubbles, sheets, menu)**: `#ffffff`
- **Surface tint (table headers, code blocks, queued bubble)**: `#f2ecfa` / `#f5f0fa`
- **Border**: `#e5dcf3` (standard), `#e2d7f0` (inputs/icon buttons), `#e9e2f4` / `#eee8f8` (nested)
- **Text primary**: `#2b2140`
- **Text secondary**: `#372c52` (bubble copy), `#756a8f` (muted/meta), `#9086a8` (tertiary/labels)
- **Accent (purple)**: `#6b4fae`, hover `#5a3f9c`
- **Accent tint (user bubble, active toggle bg)**: `#ece3f9`, text `#3a2a5c`
- **Macro colors**: protein `#ef6b52`, fat `#f2c14e`, carbs `#4fb987`
- **Status**: online `#4fb987`, retrying `#c99a2a`, offline `#d1533e`, destructive text `#c1503a` / border `#ecb9ac` / hover bg `#fbeceb`
- **Typography**: IBM Plex Sans (UI text, 400/500/600), IBM Plex Mono (all numeric/meta/data — calorie counts, timestamps, table content, labels)
- **Radius scale**: 6–9px (controls/checkboxes), 12–14px (bubbles/cards/inputs), 16–18px (sheets/menus)
- **Breakpoints**: phone <620px, tablet 620–1079px, desktop ≥1080px (chat column caps at 660px; desktop shell caps at 1440px with the chart panel fixed at 420px)

## Assets
No custom icons/images — mic and speaker icons are inline SVG (stroke `currentColor`, 18×18, 2px stroke). No photo/camera UI (explicitly out of scope).

## Files
- `ChatDiet.dc.html` — the full design reference (self-contained; open directly in a browser). Includes a Tweaks-style prop surface for previewing `viewport` (phone/tablet/desktop), `connection` state, `goalSet`, and `showQueuedMessage` — useful as a state-matrix reference during implementation, not something to port as-is.
