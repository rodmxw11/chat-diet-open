# chat-diet vs. MacroFactor's Food Logging Speed Index

*Assessment, 2026-09-08.*

Source: https://macrofactor.com/fastest-food-logger/

## About the source

MacroFactor's page introduces the **Food Logging Speed Index (FLSI)**, their own methodology for
ranking diet-tracking apps by logging speed. Rather than timing users or making qualitative
claims, they define speed strictly as the number of discrete actions (taps/steps) required to
complete four standardized logging tasks, run at each app's fastest available settings. The page
lists concrete rules for the comparison: test the premium tier if a freemium split exists, use
only "reasonable" actions (no custom gestures), and don't penalize an app for settings that are
incompatible across use cases.

The four tasks are: logging two foods through search with a non-default serving and a 3-digit
quantity (their "strong" bar: under 16 actions), logging two already-known foods at their default
serving via multi-add (under 9), scanning a barcode with a non-default serving and 3-digit
quantity (under 10), and a "quick add" of an arbitrary calorie value with no food attached (under
7). They also list "freebies" - handicaps like scrolling to a workflow's launch point or
autocomplete-selection taps - that get exempted from competitors' scores but not their own.
MacroFactor reports scoring 26 actions total (gold medal), with LoseIt and MyNetDiary tied for
second at 32.

## Methodology applied to chat-diet

chat-diet has no tap-counting UI to walk through the way a form-based logger does - its primary
interaction is a chat message (type text, hit Send) plus a small set of dedicated buttons (barcode
scan, quantity prompt). To compare fairly, "type a sentence" is counted as one action here, the
same way MacroFactor's own methodology almost certainly counts "type a search query" as one action
rather than per keystroke - their own <16 threshold for two foods only makes sense at roughly 8
actions/food if typing is a single step, which matches this counting convention.

| # | Use case | MacroFactor "strong" | chat-diet actions | Flow |
|---|---|---|---|---|
| 1 | **Search**: 2 foods, non-default serving + 3-digit qty | < 16 | **2** (best case) – **~6** (worst case) | Type `"182g grilled chicken and 340g jasmine rice"` → Send. Both items resolve in one LLM turn if either is already known or unambiguous (2 actions total for *both* foods). If a food is genuinely new/ambiguous, add one clarifying round (type a pick + Send) per item - worst case ~6. |
| 2 | **Multi-add**: 2 foods, default/recent servings | < 9 | **2** | Type `"1 chicken breast and 1 rice"` → Send. Exact-alias hits resolve silently; this is the deterministic fast path's sweet spot. |
| 3 | **Scanning**: barcode, non-default serving + 3-digit qty | < 10 | **5** | Tap scan icon → tap Capture → type quantity → tap unit toggle (to override the default "g") → tap Log. |
| 4 | **Quick-add**: arbitrary calories, no food | < 7 | **Not applicable** - see note below | No foodless quick-add exists; every entry needs a food description. |

**Where it wins:** categories 1 and 2 don't just clear the threshold, they beat MacroFactor's own
numbers outright at 2 actions each, because chat-diet can log *multiple foods in a single message*
- a paradigm their tap-counting methodology has no category for. That's the direct payoff of the
auto-match/alias-learning work done this session: a second mention of "chicken breast" now
resolves silently instead of re-asking, and the deterministic fast path skips the model entirely
for the common case.

**Where it's comparable:** barcode scanning (5 vs. the <10 bar) is solidly within range but not a
standout - MacroFactor's own scan flow is likely closer to the theoretical minimum for that
category.

**Methodology caveat:** these counts are grounded in the exact chat/barcode/fast-path logic
implemented and tested earlier this session, not a fresh timed walkthrough of the live app. A
harder number would mean driving the real app end-to-end (e.g. via Playwright) the way the
chart-click feature was verified.

## Note on category 4 (quick-add calories, no food)

This is not a gap to close. The user's explicit preference: calories are always associated with a
specific food - raw, food-less calorie entry will not be used. MacroFactor's category 4 rewards a
workflow chat-diet's user has no intention of using, so scoring "not applicable" here reflects a
deliberate product fit, not a missing feature.
