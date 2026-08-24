# chat-diet UI/UX design spec

A brief for exploring look-and-feel in Claude Design. This describes *what* the app needs to show and let the user do, and how it should adapt across screen sizes — not implementation (no component names, CSS, or library choices). See "Feeding a design back" at the end for how to hand results back for implementation.

## What the app is

A personal, single-user diet-tracking PWA built around a text chat: the user tells it what they ate, weighed, etc. in natural language, and it logs structured data behind the scenes. Everything else — goal tracking, charts, a shopping list — supports that core chat loop without competing with it for attention. Tone: plain, quiet, utilitarian. No gamification, no celebratory animations, no marketing polish. This is a tool the user opens several times a day for ten seconds at a time, standing in a kitchen or a store aisle.

## Screens / surfaces

### 1. Main chat screen (the home screen, always present)

**Structure, top to bottom:** a slim header, a scrollable conversation area filling the remaining space, a text input pinned at the bottom.

**Header shows:**
- App identity (small, unobtrusive — this isn't the point of interest).
- Today's calorie status: goal, consumed so far, and remaining, at a glance (e.g. "1,150 left of 2,000") — if no goal is set yet, a quiet prompt to set one instead of a blank/zero value.
- A small connectivity status light: green (online), yellow (trying to reconnect), red (offline, given up trying). Tapping/clicking it when red offers a manual "try again."
- A menu/pulldown entry point for: the two charts (below), shopping mode, and viewing/managing any offline-queued messages.

**Conversation area:**
- Alternating user/assistant message bubbles, newest at the bottom, auto-scrolls to the latest on new messages.
- Assistant replies can occasionally include: an inline line chart (existing "show me a chart" chat feature — small, embedded in the bubble), a small data table (existing SQL-answer feature), or — new — a checklist of food items to pick from when the user asks for a "food item shopping list" (checkboxes + a confirm action, inline in the bubble, same visual family as the chart/table cases).
- A quietly-queued state for a message the app couldn't send yet (offline): visually distinct from a normal sent message (e.g. muted/pending styling) so it's obvious at a glance that it hasn't gone anywhere yet.

**Input area:**
- Single-line growable text field, a send action, a clear/reset action.
- Existing voice-to-text and text-to-speech toggles stay.
- No photo/camera control anymore (removed).

### 2. Calorie & macro chart (on-demand, not part of chat)

Shows the last 7 or 30 days (user's choice), one column per day: how many grams of protein, fat, and carbs were eaten that day, stacked as three visually distinct blocks sized by amount, with the day's total calorie count called out above each column. The point is a quick visual read of "am I roughly hitting my macro/calorie targets lately," not precise data entry. **Static — no hover states, tooltips, zooming, or click targets.** Reference look: three stacked colored segments per day (protein / fat / carbs, each a different hue), a rounded calorie badge above each day's stack, day-of-week labels along the bottom.

### 3. Weight trend chart (on-demand, not part of chat)

Fixed to the last 30 days. Shows: the user's actual individual weigh-ins as discrete points (scattered, since they don't log every day), a smoothed trend line running through them (this is the "real" signal — daily weight bounces around, the trend is what matters), and a straight dashed goal line showing the trajectory the user is aiming for. A small data table underneath lists each day with its logged weight (if any), the smoothed trend value, and the day-over-day variance. **Static — no interactivity.**

### 4. Shopping mode (on-demand, not part of chat)

A single ordered checklist of shopping-list items. Checking an item off visually mutes it (grayed, perhaps struck through) and it settles to the bottom of the list, out of the way, but stays visible — nothing disappears. Unchecking an item restores it to its normal place among the still-pending items. This should read like ticking off a physical paper list while walking a store aisle: minimal chrome, large easy-to-tap rows, instant visual feedback per tap.

### 5. Offline queue panel (on-demand, reachable from the status light / menu)

A simple list of chat messages waiting to be sent (each shows its text and roughly when it was queued), with a way to remove/delete an individual queued message the user no longer wants sent. A manual "try to reconnect now" action lives here too when the status is red.

## Key interactions, end to end

- **Type/speak a message → send.** The dominant interaction, must stay effortless and fast above everything else on this list.
- **Set or change today's calorie goal** — via chat, in plain language (e.g. "set my goal to 1800").
- **Open a chart** — a menu tap/click (or, secondarily, a chat phrase) opens one of the two charts. On a wide screen these same two charts can simply sit visibly in a side panel instead of requiring a tap at all — see responsiveness below.
- **Add to / manage the shopping list** — either by chat ("add carrots to the list") or by picking from a "food item shopping list" checklist the assistant can show inline in the chat.
- **Enter shopping mode, check items off while shopping, uncheck by mistake.**
- **Go offline mid-conversation** — keep typing/sending normally; messages visibly queue instead of erroring; status light reflects reality; queued messages send automatically once back online, in the order they were sent; user can inspect/cancel anything still queued.

## Information always visible or one tap away

- Today's calorie goal / consumed / remaining (header, always visible).
- Connectivity status (header, always visible).
- Full chat history for the current "day" (the app's day boundary isn't necessarily midnight — don't assume a fixed cutoff visually, just show what's returned).
- 7 or 30 day macro/calorie trend (one tap, or persistent side panel on desktop).
- 30-day weight trend vs. goal (one tap, or persistent side panel on desktop).
- Shopping list, with clear pending-vs-done grouping (one tap).
- Any not-yet-sent messages (one tap, from the status light).

## Responsiveness

One design, not separate mobile/desktop versions — the same information and actions exist everywhere, only the arrangement changes.

**Phone (roughly 360–430px wide, the primary target):** everything stacked vertically — header, then the chat filling all remaining space and scrolling on its own (header and input never scroll away), then the input pinned at the bottom. No horizontal scrolling anywhere, ever. Charts and shopping mode open as a bottom sheet or full-screen overlay rather than a tiny modal — comfortable to use one-handed. Tap targets sized generously (a thumb, not a cursor). Respect the phone's rounded corners/notch/home-indicator area — nothing should sit under them. Text inputs large enough that the phone doesn't zoom in when you tap them.

**Tablet (roughly 768px):** same single-column arrangement as phone, just with more breathing room — wider bubbles, more comfortable spacing — not a fundamentally different layout yet.

**Small laptop / desktop (roughly 1024px and up, exact cutoff to be tuned by eye rather than assumed — a cramped layout at 1024 is worse than waiting a bit wider):** the chat keeps its comfortable, human-scale column width in the center/left rather than stretching edge-to-edge (a chat column spanning a 27" monitor is hard to read). The two charts move out of the "tap to open" pattern entirely and live permanently in a side panel next to the chat, always visible without an extra click. Shopping mode and the offline queue panel can stay as overlays even on desktop since they're occasional, not persistent, concerns.

**Everywhere:** dark mode support (the app already adapts to system light/dark preference — any new screens should too), and nothing should ever require horizontal scrolling or produce visibly empty dead space that looks like a mistake.

## Explicitly out of scope for this design pass

Photo capture/upload UI, recipe browsing/creation UI, any cost/price fields anywhere, a "requirements vs. notes" distinction (there's only ever "notes" now).

---

## Feeding a design back to me

Once you have something from Claude Design you like, the most useful things to hand back (any subset is fine, more is better):

1. **Screenshots or exported images** at a few representative widths — at minimum one phone-width and one desktop-width view of the chat screen, plus each of the two charts and shopping mode. I can read images directly, so this alone gets me most of the way.
2. **Actual values, not just a look** — if the design tool lets you export design tokens (colors, spacing scale, font sizes/families, corner radii), share those; otherwise just tell me things like "background is this hex," "buttons are this rounded," "spacing feels like roughly 12–16px between elements" and I'll match them.
3. **A link**, if the design lives somewhere shareable (e.g. a Figma file or a hosted preview) — paste the URL and I'll fetch what I can from it.
4. **Called-out deltas from this spec**, in plain language, if the design changed the *arrangement* of anything described above (e.g. "the status light moved into the input area" or "shopping mode is a full page, not a sheet, even on phone") — that matters more to me than pixel-perfect color matching.

I'll translate whatever you bring back into the actual CSS/component implementation described in `docs/chatdiet-rewrite-plan.md` (§9 in particular) rather than needing a pixel-exact handoff — a clear visual reference plus a few concrete values is enough.