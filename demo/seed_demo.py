"""Fill a chat-diet database with generated demo data.

Usage:  python seed_demo.py [path/to/chat-diet-demo.db]

Defaults to the copy next to this script. The target must already have the schema: point the
app at a fresh database path once so Liquibase creates it, stop the app, then run this.
"""
import sqlite3, random, sys, datetime as dt
from pathlib import Path

DB = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent / "chat-diet-demo.db"
if not DB.exists():
    sys.exit(f"{DB} does not exist - create the schema first (see demo/README.md)")

random.seed(20260925)

con = sqlite3.connect(DB)
cur = con.cursor()
if not cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='food_entry'").fetchone():
    sys.exit(f"{DB} has no schema - boot the app against it once so Liquibase creates the tables")

# Every table holding user data. Build against a database Liquibase created fresh rather than a
# copy of a real one: SQLite leaves deleted rows in free pages until VACUUM, so a copy-then-delete
# demo file can still carry recoverable real data into a public repo.
for t in ["food_entry","weight_entry","vitals_entry","exercise_entry","digestive_event",
          "note","chat_message","daily_target","daily_macro_cache","omron_reading",
          "food_item","food_alias","portion_unit","saved_query","shopping_item_archived"]:
    cur.execute(f"DELETE FROM {t}")
    cur.execute("DELETE FROM sqlite_sequence WHERE name=?", (t,))

TODAY = dt.date.today()
START = TODAY - dt.timedelta(days=59)


def epoch_ms(d):
    """Columns declared `date` (chat_message.metabolic_date, daily_target.target_date) are written
    by Spring Data JDBC as epoch milliseconds at LOCAL midnight, not ISO text. Insert text there
    and the reads come back empty with no error - the chat history and calorie goal just vanish."""
    return int(dt.datetime.combine(d, dt.time.min).timestamp() * 1000)

# name, kcal, protein, carbs, fat, fiber, sugar, sodium, satfat, chol, potassium, serving_g
FOODS = [
    ("Oatmeal, cooked",          71,  2.5, 12.0, 1.5, 1.7, 0.3,   4, 0.3,  0, 70, 240),
    ("Greek yogurt, plain",      59, 10.0,  3.6, 0.4, 0.0, 3.2,  36, 0.1,  5,141, 170),
    ("Blueberries",              57,  0.7, 14.5, 0.3, 2.4, 10.0,  1, 0.0,  0, 77, 148),
    ("Banana",                   89,  1.1, 22.8, 0.3, 2.6, 12.2,  1, 0.1,  0,358, 118),
    ("Chicken breast, grilled",  165,31.0,  0.0, 3.6, 0.0, 0.0,  74, 1.0, 85,256, 150),
    ("Brown rice, cooked",      123,  2.7, 25.6, 1.0, 1.6, 0.4,   4, 0.2,  0, 43, 195),
    ("Broccoli, steamed",        35,  2.4,  7.2, 0.4, 3.3, 1.4,  41, 0.0,  0,293, 156),
    ("Salmon fillet, baked",    206, 22.1,  0.0,12.4, 0.0, 0.0,  61, 2.5, 63,384, 150),
    ("Sweet potato, roasted",    90,  2.0, 20.7, 0.2, 3.3, 6.5,  36, 0.1,  0,475, 200),
    ("Whole wheat bread",       247, 13.0, 41.0, 3.4, 7.0, 6.0, 450, 0.7,  0,250,  28),
    ("Eggs, scrambled",         149, 10.0,  1.6,11.0, 0.0, 1.4, 145, 3.3,373,132, 100),
    ("Almonds",                 579, 21.2, 21.6,49.9,12.5, 4.4,   1, 3.8,  0,733,  28),
    ("Black coffee",              2,  0.3,  0.0, 0.0, 0.0, 0.0,   5, 0.0,  0, 49, 240),
    ("Mixed green salad",        20,  1.5,  3.6, 0.2, 2.0, 1.0,  28, 0.0,  0,230, 100),
    ("Olive oil",               884,  0.0,  0.0,100.0,0.0, 0.0,   2,13.8,  0,  1,  14),
    ("Cottage cheese, low-fat",  81, 11.0,  4.3, 2.3, 0.0, 4.1, 364, 1.4,  9, 84, 226),
    ("Apple",                    52,  0.3, 13.8, 0.2, 2.4, 10.4,  1, 0.0,  0,107, 182),
    ("Lentil soup",              67,  4.6, 11.0, 0.9, 4.4, 1.4, 320, 0.1,  0,180, 245),
    ("Tuna, canned in water",    96, 21.0,  0.0, 0.8, 0.0, 0.0, 247, 0.2, 36,201, 142),
    ("Dark chocolate, 70%",     598,  7.8, 45.9,42.6,10.9,24.0,  20,24.5,  3,715,  20),
]

for f in FOODS:
    cur.execute("""INSERT INTO food_item
        (name, per100g_calories, per100g_protein, per100g_carbs, per100g_fat,
         per100g_fiber, per100g_sugar, per100g_sodium_mg, per100g_saturated_fat,
         per100g_cholesterol_mg, per100g_potassium_mg, typical_serving_g,
         lookup_source, use_count, last_used_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'FDC',0,?)""",
        (f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10], f[11],
         TODAY.isoformat()))
con.commit()

items = cur.execute("SELECT id,name,per100g_calories,per100g_protein,per100g_carbs,"
                    "per100g_fat,per100g_fiber,per100g_sugar,per100g_sodium_mg,"
                    "per100g_saturated_fat,per100g_cholesterol_mg,per100g_potassium_mg,"
                    "typical_serving_g FROM food_item").fetchall()
by_name = {r[1]: r for r in items}

BREAKFAST = ["Oatmeal, cooked","Greek yogurt, plain","Eggs, scrambled"]
LUNCH     = ["Chicken breast, grilled","Lentil soup","Tuna, canned in water","Mixed green salad"]
DINNER    = ["Salmon fillet, baked","Chicken breast, grilled","Brown rice, cooked",
             "Broccoli, steamed","Sweet potato, roasted"]
SNACK     = ["Apple","Banana","Almonds","Blueberries","Dark chocolate, 70%","Cottage cheese, low-fat"]

def log(day, hour, minute, name, grams):
    r = by_name[name]
    k = grams / 100.0
    cur.execute("""INSERT INTO food_entry
        (logged_at, raw_utterance, total_calories, total_protein_g, total_carbs_g,
         total_fat_g, fiber_g, sugar_g, sodium_mg, saturated_fat_g, cholesterol_mg,
         potassium_mg, source, food_item_id, amount_grams)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'CHAT',?,?)""",
        (f"{day.isoformat()}T{hour:02d}:{minute:02d}:00.000000",
         f"{grams:.0f}g {r[1].split(',')[0].lower()}",
         round(r[2]*k), round(r[3]*k,1), round(r[4]*k,1), round(r[5]*k,1),
         round(r[6]*k,1), round(r[7]*k,1), round(r[8]*k,1), round(r[9]*k,1),
         round(r[10]*k,1), round(r[11]*k,1), r[0], grams))

weight = 212.4
for i in range(60):
    day = START + dt.timedelta(days=i)

    log(day, 7, 35, "Black coffee", 240)
    log(day, 8, 10, random.choice(BREAKFAST), random.choice([220,260,300]))
    log(day, 8, 15, random.choice(["Blueberries","Banana","Almonds"]), random.choice([28,118,148]))
    log(day, 12, 30, random.choice(LUNCH), random.choice([200,240,280]))
    log(day, 12, 35, "Whole wheat bread", random.choice([56,84]))
    log(day, 15, 20, random.choice(SNACK), random.choice([28,120,182,226]))
    log(day, 18, 30, random.choice(DINNER), random.choice([180,200,220]))
    log(day, 18, 35, random.choice(["Broccoli, steamed","Mixed green salad","Brown rice, cooked",
                                    "Sweet potato, roasted"]), random.choice([156,195,200]))
    if random.random() < 0.5:
        log(day, 18, 36, "Olive oil", 14)
    if random.random() < 0.35:
        log(day, 20, 45, "Dark chocolate, 70%", 20)

    # weight: downward trend with daily noise, ~5 weigh-ins per week
    weight -= random.uniform(0.15, 0.32)
    if random.random() < 0.8:
        cur.execute("INSERT INTO weight_entry (logged_at, weight_lbs) VALUES (?,?)",
                    (f"{day.isoformat()}T06:50:00.000000",
                     round(weight + random.uniform(-0.9, 0.9), 1)))

    # blood pressure AM/PM
    if random.random() < 0.85:
        cur.execute("INSERT INTO vitals_entry (logged_at, systolic, diastolic, heart_rate) VALUES (?,?,?,?)",
                    (f"{day.isoformat()}T07:05:00.000000",
                     random.randint(118,134), random.randint(74,84), random.randint(58,70)))
    if random.random() < 0.6:
        cur.execute("INSERT INTO vitals_entry (logged_at, systolic, diastolic, heart_rate) VALUES (?,?,?,?)",
                    (f"{day.isoformat()}T20:15:00.000000",
                     random.randint(115,130), random.randint(70,82), random.randint(60,74)))

    if random.random() < 0.5:
        name, mins = random.choice([("walking",45),("stationary bike",30),
                                    ("swimming",35),("weight training",40),("hiking",60)])
        cur.execute("""INSERT INTO exercise_entry (logged_at, exercise_name, duration_minutes, calories_burned)
                       VALUES (?,?,?,?)""",
                    (f"{day.isoformat()}T17:15:00.000000", name, mins, int(mins*random.uniform(4.5,7.5))))

    cur.execute("INSERT INTO daily_target (target_date, target_calories) VALUES (?,?)",
                (epoch_ms(day), 1800))

for txt, back in [
    ("Switching to a 1,800 cal target starting this week", 46),
    ("Walking after dinner seems to help the evening BP readings", 33),
    ("Prep salmon + sweet potato on Sundays - makes weeknights much easier", 21),
    ("Down a belt notch", 12),
    ("Sleep has been better since cutting the late chocolate", 5),
]:
    d = TODAY - dt.timedelta(days=back)
    cur.execute("INSERT INTO note (logged_at, text) VALUES (?,?)",
                (f"{d.isoformat()}T19:30:00.000000", txt))

for et, notes, back in [("reflux","after a late dinner",18),("bloating",None,9)]:
    d = TODAY - dt.timedelta(days=back)
    cur.execute("INSERT INTO digestive_event (logged_at, event_type, notes) VALUES (?,?,?)",
                (f"{d.isoformat()}T21:00:00.000000", et, notes))

CHAT = [
    ("user","240g black coffee", 7,36),
    ("assistant","Logged: black coffee, 240g (5 cal).", 7,36),
    ("user","200g oatmeal and a banana", 8,11),
    ("assistant","Logged: oatmeal 200g (142 cal, 5.0g protein), banana 118g (105 cal, 1.3g protein).", 8,11),
    ("user","how many calories do I have left today?", 12,15),
    ("assistant","You've had 247 of 1,800 calories so far, leaving 1,553 for the rest of the day.", 12,15),
    ("user","170g grilled chicken breast and a green salad", 12,31),
    ("assistant","Logged: chicken breast 170g (281 cal, 52.7g protein), mixed green salad 100g (20 cal, 1.5g protein).", 12,31),
    ("user","weight 198.2", 6,51),
    ("assistant","Logged weight: 198.2 lbs.", 6,51),
    ("user","chart my weight over the last month", 13,2),
    ("assistant","Here you go.", 13,2),
]
for role, content, h, m in CHAT:
    cur.execute("""INSERT INTO chat_message (metabolic_date, role, content, created_at,
                   prompt_tokens, completion_tokens, total_tokens)
                   VALUES (?,?,?,?,?,?,?)""",
                (epoch_ms(TODAY), role, content,
                 f"{TODAY.isoformat()}T{h:02d}:{m:02d}:00.000000",
                 random.randint(900,1400) if role=="assistant" else None,
                 random.randint(20,90) if role=="assistant" else None,
                 random.randint(950,1500) if role=="assistant" else None))

for desc, back in [("oatmeal", 3), ("frozen broccoli", 2), ("greek yogurt", 1)]:
    d = TODAY - dt.timedelta(days=back)
    cur.execute("""INSERT INTO shopping_item_archived (description, status, added_at)
                   VALUES (?, 'PENDING', ?)""", (desc, f"{d.isoformat()}T09:00:00.000000"))

cur.execute("""INSERT INTO saved_query (name, description, sql_text, use_count, last_used_at)
               VALUES ('Top foods by frequency','Most frequently logged foods',
               'SELECT fi.name, COUNT(*) n FROM food_entry fe JOIN food_item fi ON fi.id=fe.food_item_id GROUP BY fi.name ORDER BY n DESC',
               4, ?)""", (TODAY.isoformat(),))

# The blood-pressure chart reads omron_reading, NOT vitals_entry - seed only vitals and the page
# says "No readings yet". Timestamps here are text to minute precision.
cur.execute("""INSERT INTO omron_reading (timestamp, systolic, diastolic, bpm)
               SELECT substr(logged_at, 1, 16), systolic, diastolic, heart_rate FROM vitals_entry""")

# daily_macro_cache is deliberately left empty: the app rebuilds it on boot, and seeding it here
# produces a second set of rows that collide with the app's on its unique metabolic_date index.

cur.execute("UPDATE food_item SET use_count = (SELECT COUNT(*) FROM food_entry WHERE food_item_id = food_item.id)")

con.commit()
print("food_entry :", cur.execute("SELECT COUNT(*) FROM food_entry").fetchone()[0])
print("weight     :", cur.execute("SELECT COUNT(*) FROM weight_entry").fetchone()[0])
print("vitals     :", cur.execute("SELECT COUNT(*) FROM vitals_entry").fetchone()[0])
print("exercise   :", cur.execute("SELECT COUNT(*) FROM exercise_entry").fetchone()[0])
print("weight rng :", cur.execute("SELECT MIN(weight_lbs), MAX(weight_lbs) FROM weight_entry").fetchone())
print("days       :", cur.execute("SELECT COUNT(DISTINCT date(logged_at)) FROM food_entry").fetchone()[0])
con.close()
