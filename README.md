# FinLedger

Personal finance for India. Bank statements go in; budgets, bill reminders,
who owes whom, and what your mutual funds are really returning come out.

![Dashboard](docs/images/dashboard.png)

Import CSV statements from HDFC, ICICI or SBI, or add transactions by hand.
FinLedger sorts them into categories, finds the payments that repeat and
reminds you before they are due, warns you when a budget is running out,
splits trip and flat costs with friends to the paisa, and values your mutual
funds at the latest NAV with an XIRR you can trust. It is a Spring Boot 4 and
MySQL back end with a React front end, run with Docker Compose.

- [Requirements](#requirements)
- [Running it](#running-it)
- [What you can do](#what-you-can-do)
- [Money is counted in paise](#money-is-counted-in-paise)
- [Importing statements](#importing-statements)
- [Budgets and alerts](#budgets-and-alerts)
- [Bills that repeat](#bills-that-repeat)
- [Splitting with friends](#splitting-with-friends)
- [Mutual funds](#mutual-funds)
- [Logging in](#logging-in)
- [How it holds together](#how-it-holds-together)
- [Tests](#tests)
- [Screenshots](#screenshots)
- [Future work](#future-work)
- [License](#license)

## Requirements

Docker is the shortest path and needs nothing else installed.

- Docker Desktop, or Docker Engine with Compose v2
- An internet connection when the app starts, for mutual fund prices from
  [mfapi.in](https://www.mfapi.in). Everything else works offline.

To run it without Docker, or to run the tests:

- JDK 21. The Maven wrapper is in the repository, so Maven itself is not needed.
- Node 22.12 or newer.
- MySQL 8.4. The database container from `docker-compose.yml` is the easy way.

## Running it

```bash
git clone https://github.com/kaushiksridhar17/finledger.git
cd finledger
cp .env.example .env
```

On Windows PowerShell, use `Copy-Item .env.example .env` for the last line.

Open `.env` and set `JWT_SECRET` to a random string of at least 32
characters. It signs every login, so it should not be the example value.
`openssl rand -base64 48` makes a good one (Git Bash has `openssl` on
Windows). Then:

```bash
docker compose up --build
```

Open http://localhost:3000 and click **Try the demo**, or create an account.

The demo gives you a throwaway account with a year of transactions across a
bank account, a credit card, cash and a wallet. It also comes with budgets,
confirmed bills, two split groups and a small mutual fund portfolio. Every
click makes a separate account, deleted after 24 hours.

> **Development only.** `.env.example` ships development passwords for MySQL.
> Change them in `.env` before the first run if the machine is shared. MySQL
> is published on port 3307 of your machine, not 3306, so it does not clash
> with a MySQL you already have.

### Without Docker

Run the database in Docker and the other two yourself.

```bash
docker compose up -d db
```

```bash
cd backend
./mvnw spring-boot:run        # .\mvnw.cmd spring-boot:run on Windows
```

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Vite passes `/api` through to the backend on port
8080. The backend's defaults in `application.properties` match `.env.example`,
so it needs no extra configuration.

### Useful commands

```bash
docker compose up --build -d   # rebuild after pulling changes, run in the background
docker compose logs -f backend # follow the backend's log
docker compose down            # stop, keep the data
docker compose down -v         # stop and wipe the database
```

`down -v` deletes every account, transaction and cached fund price. The
database is rebuilt from the migrations on the next start.

## What you can do

- **Track accounts and transactions.** Bank accounts, credit cards, wallets
  and cash, with balances worked out from the transactions rather than stored.
  Search and filter by account, category, direction and date.
- **Import bank statements.** Upload a CSV and the rows are added and sorted
  into categories. Rows you already have are skipped.
- **Set budgets.** A monthly limit per category, with a notification at 80%
  and another if you go over.
- **See bills coming.** Repeating payments are found in your history. Confirm
  the ones that are bills and you get a reminder three days before each.
- **Split costs.** Groups for trips or flats, four ways to split an expense,
  the fewest payments that settle everyone, and UPI links to pay.
- **Follow your mutual funds.** Real NAVs, gains, XIRR, and SIPs picked up from
  your bank statement.

![Transactions](docs/images/transactions.png)

## Money is counted in paise

Every amount is a whole number of paise in a `BIGINT` column, never a float.
₹1,299.50 is stored as 129950. The frontend turns typed text into paise by
reading the digits, not by multiplying a JavaScript number by 100, so "0.1"
becomes exactly 10 paise.

Where a division has to happen, the remainder is handed out explicitly: see
[splitting](#splitting-with-friends). Unit counts and NAVs, which are not
money, are exact `DECIMAL`s. Amounts are shown with Indian digit grouping,
₹1,23,456.50, done by hand on the server because Java's formatters do not do
lakhs.

## Importing statements

![Import](docs/images/import.png)

Upload a CSV and pick the account it belongs to. The format is recognised from
the header row: HDFC, ICICI, SBI, or a simple Date, Description, Amount
template for everything else. The CSV reader is written by hand and follows
RFC 4180, so quoted fields with commas and line breaks, Excel's byte order
mark and all three kinds of line ending are handled.

The import runs in the background on a small worker pool, so a large file does
not hold up the request. The page shows it moving from waiting to done, with
how many rows were added, skipped as duplicates, or could not be read, each
with its line number.

**Duplicates.** Statements overlap: download 15 August to 15 September after
you already imported August, and half the rows are ones you have. Each row
gets a fingerprint, a SHA-256 of the account, date, amount and cleaned-up
description. A unique key on it makes the database refuse a second copy. Two
genuinely identical rows on one day, like two ₹20 chai payments, are told
apart by counting occurrences within the file, so both survive. Imports into
the same account are run one at a time, by locking the account's row, so two
uploads at once cannot both decide a row is new.

**Categories.** Your own rules are checked first, oldest first: "when the
description contains CHAI POINT, use Food & Dining". Then about sixty built-in
rules for common Indian merchants run. They match whole words, so a short
merchant name cannot fire inside a longer word, and each knows whether it is
for money in or money out, so a refund from Swiggy is not filed as food
spending.

## Budgets and alerts

![Budgets](docs/images/budgets.png)

A budget is a monthly limit for one spending category. Spending is the net of
the category for the month, so a refund brings it back down.

When a transaction is saved, edited or deleted, or an import finishes, the
budgets are checked. You get one notification when a category passes 80% and
one when it passes 100%, never repeated for the same month. Each notification
has a key like `budget:12:2026-09:80` with a unique constraint on it, so even
two checks racing each other cannot send it twice.

![Notifications](docs/images/notifications.png)

The checks run after the transaction that caused them has committed, and in a
database transaction of their own. If an alert goes wrong, it is logged and
dropped: it can never turn your successful save into an error.

## Bills that repeat

![Bills](docs/images/bills.png)

Nobody types in their subscriptions, so FinLedger finds them. It groups your
transactions by merchant, or by a cleaned-up description with reference
numbers removed, and looks at the gaps between payments. The median gap picks
the frequency: weekly, monthly, quarterly or yearly. A series counts when at
least three quarters of its gaps fit that frequency, the amounts stay within
50% of the typical one (electricity bills vary), and it is still happening.

Found series arrive as suggestions. Confirm the bills and you get a reminder
three days before each is due. Dismiss the rest and they stay dismissed:
rescanning never overrides your choice. Money coming in, like salary, is
detected the same way and shown as expected income. A nightly job at 2:30 am
Indian time rescans everyone with recent activity and sends the reminders.

## Splitting with friends

![A split group](docs/images/split-group.png)

A group is a trip or a flat, with you and the friends you share costs with.
Friends are just names with an optional UPI ID; they do not need an account.

![Adding an expense](docs/images/split-expense.png)

An expense can be split equally, by exact amounts, by percentages or by
shares, like two nights against one. ₹100 three ways is 33.34, 33.33 and
33.33. Everyone's exact fraction is rounded down, and the spare paise go to
whoever was rounded down the most, so the shares always add up to the total
exactly. The form works each share out as you type, and the server checks it
again.

**Settling up.** Only each person's net position matters. If Neha owes Rohan
₹500 and Rohan owes you ₹500, Neha can pay you and Rohan is out of it. The
settle-up plan first pairs anyone who owes exactly what someone else is owed,
then has the biggest debtor pay the biggest creditor until everyone is square.
Finding the true minimum number of payments is NP-hard, so this greedy plan is
not always optimal. With n people it never needs more than n - 1 payments, and
it always settles everyone exactly. The tests check both over a thousand
random groups.

![Paying with UPI](docs/images/split-upi.png)

Each payment in the plan has a `upi://pay` link with the payee, amount and
note filled in, shown as a QR code. On a phone, the link opens GPay, PhonePe
or Paytm directly.

**Spotting repayments.** After any change to your transactions, money that
came in over the last 45 days is compared with what each friend owes you in
the plan. A credit for the right amount, give or take a rupee, that names the
friend or their UPI ID is suggested as their repayment. Say yes and it is
recorded, linked to the bank transaction. Say no and that transaction is never
suggested for them again.

## Mutual funds

![Investments](docs/images/investments.png)

Search any Indian mutual fund by name and record purchases and redemptions.
Fund details and the full daily NAV history come from mfapi.in, a free API over
AMFI's published NAVs. They are cached in MySQL, so pages never wait on the
internet. A job at 11:40 pm Indian time, after AMFI publishes, fetches the
day's NAVs for every fund in use. If mfapi.in is down, everything keeps working
on the last NAV it had; only adding a fund nobody has used before has to wait.

![Recording a purchase](docs/images/investments-add.png)

Units are worked out from the NAV of the purchase date, to three decimal
places as fund houses allot them. Money paid on a weekend gets the next
business day's NAV. Redemptions use the average cost, so selling 40% of your
units removes 40% of what they cost. A redemption is checked against what you
held on its date and every date after, so a sale cannot be recorded before the
purchase it depends on.

**XIRR.** A plain percentage gain is misleading for a SIP, where every
instalment has been invested for a different length of time. XIRR is the yearly
rate that makes all the cash flows, purchases out and today's value in, worth
zero together, the same measure Excel's `XIRR()` gives. There is no formula for
it, so it is solved numerically: Newton's method first, and bisection if that
wanders off. The unit tests check it against Excel's own worked example. It is
only shown once money has been invested for a month, because a few days'
return annualised is noise.

**SIPs from your statement.** Debits like `NACH/BSE STARMF/SIP PARAG PARIKH
FLEXI CAP` are recognised as SIPs. Link one to its fund and every instalment,
past and future, becomes a purchase. Deleting the bank transaction removes the
purchase with it.

## Logging in

![Login](docs/images/login.png)

Logging in gives the browser a short-lived access token, a signed JWT valid for
15 minutes, kept only in memory. The refresh token is in an `HttpOnly`,
`SameSite=Strict` cookie that JavaScript cannot read and that is only sent to
`/api/auth`. When the access token expires, the page quietly swaps the cookie
for a new one. Several requests expiring at once share a single refresh.

Every refresh rotates the cookie, and only a SHA-256 of it is stored. If an
old refresh token is ever used again after a 30-second grace period, which
covers two tabs refreshing at once, it has probably been stolen. The whole
session is then ended. Logging in with an unknown email still runs a BCrypt
check against a dummy hash, so a wrong email and a wrong password take the
same time and nobody can find out which emails have accounts.

Demo users are ordinary users with an expiry time. A cleanup job deletes them
every hour, and database cascades take their data with them. No more than 200
exist at once, so the button cannot be used to fill the database.

## How it holds together

```
Browser ──▶ nginx :3000 ──/api──▶ Spring Boot ──▶ MySQL 8.4
            (React build)           │
                                    ├─ import worker pool (2 threads)
                                    ├─ after-commit: budget alerts, repayments, SIPs
                                    ├─ scheduled: 2:30 am checks, 11:40 pm NAVs, hourly cleanup
                                    └─▶ mfapi.in (fund NAVs, cached in MySQL)
```

nginx serves the built frontend and passes `/api` to the backend, which is not
published outside Docker's network. Flyway owns the schema, migrations `V1` to
`V8`, and Hibernate only checks that the entities match it.

### Decisions worth knowing about

The database enforces what matters. Duplicate rows, repeated notifications
and repeated suggestions are all stopped by unique keys, not by checking first
and inserting after. Deleting a user, a group or a bank transaction cascades
in the database, so nothing is left pointing at nothing.

There are no boolean columns. "Archived", "is a demo user" and "read" are
nullable timestamps (`archived_at`, `demo_expires_at`, `read_at`), which say
when as well as whether.

"Today" is worked out in Indian time and timestamps are stored in UTC. The
clock is injected, so tests can move time instead of waiting for it.

The demo data comes from a fixed random seed, with dates relative to today, so
every demo looks the same and never goes stale. It goes through the same
services as real data, so it obeys the same rules.

Anything slow happens outside database transactions. Fund prices are fetched
before a request's transaction starts, and fund data is written with MySQL
upserts, so two requests fetching the same fund cannot clash.

Redemptions use average cost, which is what a portfolio view needs. Tax rules
in India use first-in-first-out, so the gains here are not capital gains for
filing.

### Layout

```
backend/src/main/java/com/kaushiksridhar/finledger/
  auth/, security/, user/   login, token rotation, the current user
  account/, category/,
  transaction/              the ledger itself
  importing/                upload, background processing, csv/ and format/ for each bank
  rules/                    built-in and user categorisation rules
  dashboard/                monthly totals and category breakdown
  budget/, recurring/,
  notification/, alerts/    budgets, bill detection, reminders, the jobs that drive them
  split/                    groups, split maths, debt simplification, repayment matching
  investment/               NAV source and cache, XIRR, holdings, SIP linking
  demo/                     demo data and cleanup
  common/                   errors, money text, time, paging
backend/src/main/resources/db/migration/   V1 to V8
frontend/src/
  api/                      one file per part of the API
  auth/                     in-memory access token and silent refresh
  components/               shared pieces, and a folder per feature
  lib/                      money, dates, splits, UPI links, each with tests
  pages/                    one per screen, loaded on demand
frontend/scripts/screenshots.mjs          takes the pictures in this README
```

## Tests

The backend tests need MySQL running. They use a separate `finledger_test`
database, created by `docker/mysql-init` the first time the database
container starts, so they never touch your data.

```bash
docker compose up -d db
cd backend
./mvnw test          # .\mvnw.cmd test on Windows
```

164 of them. Most go through the real HTTP API with MockMvc against MySQL.
Among them:

- The token rotation and reuse detection.
- Importing an overlapping statement twice.
- Splitting, settling and privacy between users.
- Repayment and SIP matching.

The pure parts are tested on their own:

- The CSV reader, the bank formats and the categoriser.
- Recurring payment detection.
- Split rounding and debt simplification, including a thousand random splits
  and a thousand random groups.
- XIRR against Excel.

Fund prices come from a fake NAV source with fixed prices, so no test needs the
internet.

The frontend has its own, covering money parsing, dates, split maths and UPI
links:

```bash
cd frontend
npm test
npm run lint
```

## Screenshots

The pictures in this README are taken from the running app by a script. With
the app running, from the `frontend` folder:

```bash
npm run screenshots              # add -- --show to watch it
```

It opens Microsoft Edge, or Chrome if there is no Edge, and clicks **Try the
demo**. Then it visits each page and writes the images to `docs/images`. It
uses the browser you already have, so there is nothing large to download.

## Future work

- PDF statements. Most banks offer PDF more readily than CSV, and only CSV is
  read.
- Notifications outside the app. Reminders only appear under the bell; email
  or push would make the three-day bill warning useful when the app is closed.
- Shared groups. A group belongs to the person who made it, and friends cannot
  log in to see it or add expenses themselves.
- Capital gains. Redemptions use average cost, so the numbers are not ready
  for a tax return, which needs first-in-first-out.
- More than one currency. Every user has a base currency column, but
  everything is in rupees.
- Deployment. It runs locally and in Docker; there is no hosted instance.

## Built with

Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Hibernate, Flyway,
MySQL 8.4, React 19, React Router, Vite, Tailwind CSS, Recharts, JUnit 5,
AssertJ, MockMvc, Vitest, Playwright, Docker, nginx.

## License

MIT, see [LICENSE](LICENSE). Copyright (c) 2026 Kaushik Sridhar.
