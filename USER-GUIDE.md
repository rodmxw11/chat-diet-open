# chat-diet User Guide

chat-diet is conversational - there's no form to fill out, just type (or say) what you want in
plain English in the chat box. The model routes your message to one of the intents below and
calls the matching tool(s) automatically.

> Parts of this guide predate the 2026-09 food-entry overhaul (recipes, shopping list, and photo
> logging have been removed). The quickest current ways to log food:
>
> - **Instant entries** - "142g cheerios", "100 cal apple sauce", "3 slices honey wheat bread",
>   with or without a leading "I ate": when the food is a known alias, these log immediately with
>   no model round trip.
> - **Calories as an amount** - "250 cal of hot dog buns" works anywhere an amount does; the app
>   converts calories to grams from the item's per-100g data.
> - **Fewer questions** - a single strong match auto-logs (echoed as *auto-matched*) instead of
>   asking, and every pick from a clarification list is remembered as an alias, so the same
>   question never repeats. Manage aliases (including auto-learned ones) on the Food Items page.
> - **Barcode scans** - after a scan resolves, a "How much of ...?" bar appears: type a number,
>   tap g / cal / servings, done. An unrecognized barcode opens the new-item form with the UPC
>   filled in, then returns to the same prompt.

<!-- TOC -->
* [chat-diet User Guide](#chat-diet-user-guide)
  * [Logging things](#logging-things)
    * [Log food](#log-food)
    * [Log weight](#log-weight)
    * [Log vitals (blood pressure / heart rate)](#log-vitals-blood-pressure--heart-rate)
    * [Log exercise](#log-exercise)
    * [Log a digestive event](#log-a-digestive-event)
    * [Save a note](#save-a-note)
  * [Correcting a recent entry](#correcting-a-recent-entry)
  * [Asking questions](#asking-questions)
    * [Calorie target, fasting status, weight projections](#calorie-target-fasting-status-weight-projections)
    * [Charts](#charts)
    * [Open-ended / analytical questions (natural language to SQL)](#open-ended--analytical-questions-natural-language-to-sql)
  * [Recipes](#recipes)
    * [Teach a new recipe](#teach-a-new-recipe)
    * [Refine an existing recipe](#refine-an-existing-recipe)
    * [Manage recipes](#manage-recipes)
  * [Shopping list](#shopping-list)
  * [App feedback](#app-feedback)
  * [Notes on how routing works](#notes-on-how-routing-works)
<!-- TOC -->

## Logging things

### Log food

Log a meal by name, by barcode scan, from a saved recipe, or from a photo.

- "I **had** a turkey sandwich and a bag of chips for lunch"
- "**Log** two scrambled eggs and toast"
- *(scan a barcode)* → confirms the product and logs it
- *(attach a food photo)* "here's what I'm eating"
- "Log my usual chicken stir fry" *(**named dish** matches a saved recipe if you have one)*

Anything under 10 calories (black coffee, water) is acknowledged but not logged.

### Log weight

- "182.4" *(a bare number is read as a weight)*
- "**Weighed in** at 181 this morning"

### Log vitals (blood pressure / heart rate)

- "**BP** was 128 over 82, **pulse** 68"
- "**Blood pressure** 130/85"

### Log exercise

- "**Ran** for 30 **minutes**"
- "**Did** 45 **minutes** of weightlifting"

### Log a digestive event

- "Had some **reflux** after lunch"
- "**Diarrhea** this morning, might be the takeout"

### Save a note

Anything you want written down that isn't food/weight/vitals/exercise.

- "**Note to self**: try the new pharmacy for refills"
- "**Remember that** I'm allergic to shellfish"

## Correcting a recent entry

Only works when the correction is linguistically marked - a bare new number is treated as a new
entry, not a correction.

- "**The correct** BP is 156 over 65" *(corrects the last vitals entry)*
- "**Make that** two slices, not one" *(corrects the last food entry)*
- "The scale **actually** said 181" *(corrects the last weight entry)*

## Asking questions

### Calorie target, fasting status, weight projections

- "How many **calories** do I have **left today**?"
- "**How long** has it been **since I ate**?"
- "**When will I hit** 175 lbs at this rate?"
- "**What will I weigh** by June 1st?"

### Charts

- "**Show me** my calories this week"
- "**Chart** my weight for the last 3 months"
- "**Plot** my macros for today"
- "**Graph** my deficit this year"

### Open-ended / analytical questions (natural language to SQL)

For anything the other tools can't answer directly - trends, rankings, counts, "favorite X".

- "What's my **most logged** food this month?"
- "**How many times** have I eaten pizza?"
- "What was my **average** weight last month?"
- "Which exercise do I do **most often**?"

## Recipes

### Teach a new recipe

- "**New recipe**: chili - ground beef, beans, tomatoes, onion"
- "I made a big pot of chicken soup, want to **save it as a recipe**"

### Refine an existing recipe

- "**Update the** chili **recipe** - I used two cans of beans, not one"

### Manage recipes

- "What recipes have I **only logged once**?" *(surfaces stale/unused recipes)*
- "**Delete the** chili **recipe**"

## Shopping list

- "**Add** milk **to the shopping list**"
- "**What's on** my shopping list?"
- "I **picked up** eggs and bread at Trader Joe's for $12"

## App feedback

Feature requests or complaints about the app itself - not your diet/health data.

- "It **would be nice if** I could edit past entries from the chart view"
- "The chart is **confusing** when I ask for last month"

## Notes on how routing works

- You don't need to name a tool or use exact phrasing - the model interprets intent from
  natural language.
- Corrections require correction language ("actually", "make that", "the correct X is..."); a
  plain restated number is logged as a new entry.
- Chart and SQL-query results render inline in the chat (a chart or table), so replies to those
  requests are intentionally brief.
- See `HOW-TO-RUN.md` for how to run the app, and `backend/openapi.yaml` /
  `http://localhost:8080/swagger-ui/index.html` for the underlying REST API.
