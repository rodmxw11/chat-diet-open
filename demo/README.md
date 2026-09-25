# Demo data

A ready-to-run database of generated data, so you can start the app and see a populated UI
without logging two months of meals first. Everything in it is invented — 60 days for a
fictional person: ~520 food entries, 49 weigh-ins trending 212 → 199 lbs, 88 blood-pressure
readings, exercise, notes and a day of chat.

## Run the app against it

From the repository root:

```
copy demo\chat-diet-demo.db backend\data\
cd backend
gradlew bootRun --args="--spring.datasource.url=jdbc:sqlite:./data/chat-diet-demo.db"
```

On macOS or Linux use `cp demo/chat-diet-demo.db backend/data/` and `./gradlew`.

Then open the app — `https://localhost:8443` by default (see [HOW-TO-RUN.md](../HOW-TO-RUN.md)).
You still need `application.yml` with an Anthropic API key for the chat box to answer, but
every other screen — charts, daily foods, micronutrients, blood pressure, food items — reads
straight from this database and works without one.

Copy the file rather than pointing the app at `demo/` directly: the app writes as it runs
(it rebuilds `daily_macro_cache` on boot), which would leave the tracked copy dirty.

## Regenerating it

`seed_demo.py` rebuilds the data. Point the app at a **fresh** database path first so
Liquibase creates the schema, stop it, then run the script against that file:

```
cd backend
gradlew bootRun --args="--spring.datasource.url=jdbc:sqlite:./data/fresh.db"   # ctrl-c once it starts
cd ..
python demo/seed_demo.py backend/data/fresh.db
```

With no argument it seeds `demo/chat-diet-demo.db` in place.

Build from a new database rather than a copy of a real one: SQLite leaves deleted rows in free
pages until `VACUUM`, so copy-then-delete can carry recoverable real data into a public repo.

Two things that fail silently if you write the data yourself:

- Columns declared `date` (`chat_message.metabolic_date`, `daily_target.target_date`) hold
  **epoch milliseconds at local midnight**, not ISO text. Text there reads back as nothing —
  the chat history and calorie goal simply come up empty, with no error.
- The blood-pressure chart reads `omron_reading`, not `vitals_entry`. Seed only the latter and
  the page says "No readings yet".

## Screenshots

`screenshots/` holds the full light-mode set captured from this data. The few used in the
top-level README live in `images/`.
