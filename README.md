# FinLedger

A personal finance app for India: bank statements in, budgets, bill reminders, split expenses and mutual fund returns out.

![Java 21](https://img.shields.io/badge/Java-21-b07219)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-6db33f)
![React 19](https://img.shields.io/badge/React-19-149eca)
![MySQL 8.4](https://img.shields.io/badge/MySQL-8.4-00758f)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ed)
![License: MIT](https://img.shields.io/badge/License-MIT-lightgrey)

![Dashboard](docs/images/dashboard.png)

- [Overview](#overview)
- [Key features](#key-features)
- [Results](#results)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Key design decisions](#key-design-decisions)
- [Getting started](#getting-started)
- [Usage](#usage)
- [API reference](#api-reference)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Challenges and learnings](#challenges-and-learnings)
- [Roadmap](#roadmap)
- [License](#license)
- [Author](#author)

## Overview

- **The problem:** money in India is spread across UPI, bank accounts, cards, SIPs and shared trips. Banks give you a CSV and nothing more.
- **The idea:** upload a bank statement once and get budgets, bill reminders, split expenses and fund returns from it, without typing anything in.
- **Try it:** the **Try the demo** button opens a throwaway account with a year of sample data.

## Key features

- **Statement import:** CSVs from HDFC, ICICI and SBI, categorised automatically, with duplicates skipped.
- **Budgets:** a monthly limit per category, with alerts at 80% and 100%.
- **Recurring bills:** repeating payments are detected and turned into reminders.
- **Bill splitting:** four ways to split, a settle-up plan with the fewest payments, and UPI links with QR codes.
- **Mutual funds:** live NAVs, gains, XIRR, and SIPs picked up from bank statements.
- **Secure login:** short-lived access tokens and rotating refresh tokens with theft detection.

## Results

- **187 automated tests** (164 backend, 23 frontend).
- **65% fewer payments** to settle a group (12.9 down to 4.5, over 1,000 simulated groups).
- **0 paise lost** to rounding across 1,000 random splits.
- **XIRR matches Excel** to 6 decimal places.
- **0 duplicates** when a statement is re-uploaded.
- **89% of rows** in the sample statements categorised automatically.
- **10 of 10 recurring bills** found in a year of demo data, with no false positives.

## Tech stack

- **Backend:** Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway.
- **Database:** MySQL 8.4.
- **Frontend:** React 19, Vite, Tailwind CSS, Recharts.
- **External data:** mfapi.in for mutual fund NAVs.
- **Testing:** JUnit 5, MockMvc, Vitest, Playwright (screenshots).
- **Infrastructure:** Docker Compose, nginx.

## Architecture

```
Browser ──▶ nginx :3000 ──/api──▶ Spring Boot ──▶ MySQL 8.4
            (React build)           │
                                    ├─ import worker pool
                                    ├─ after-commit: budget alerts, repayments, SIPs
                                    ├─ scheduled: nightly checks, daily NAVs, demo cleanup
                                    └─▶ mfapi.in (fund NAVs, cached in MySQL)
```

- nginx serves the React app and forwards `/api` to the backend.
- Statement imports are parsed in the background.
- Budget alerts, repayment matching and SIP syncing run after each save.
- Nightly jobs check bills and budgets and fetch the day's NAVs.

## Key design decisions

- **Money is stored as whole paise, never floats,** so ₹100 split three ways is exactly 33.34 + 33.33 + 33.33.
- **The database blocks duplicates** with unique keys and row fingerprints, not "check then insert".
- **Side effects never break a save:** a failed alert can't turn a successful save into an error.
- **Greedy settle-up:** the perfect answer is too slow to compute, so it uses a simple method that needs at most n − 1 payments.
- **XIRR is solved numerically** and only shown after 30 days, when it means something.
- **Tokens are built to survive theft:** the access token lives in memory, and a reused refresh token logs out the whole session.
- **No network calls inside a database transaction,** so a slow fund API never holds locks.

## Getting started

### Prerequisites

- Docker Desktop.
- Internet access when the app starts, for fund prices.
- To run without Docker: JDK 21, Node 22.12+ and MySQL 8.4.

### Installation

```bash
git clone https://github.com/kaushiksridhar17/finledger.git
cd finledger
cp .env.example .env          # Copy-Item .env.example .env on Windows PowerShell
```

### Environment variables

- `DB_NAME`, `DB_USER`, `DB_PASSWORD`: the app's MySQL database and user.
- `DB_ROOT_PASSWORD`: MySQL's root password.
- `JWT_SECRET`: a random string of 32+ characters, e.g. from `openssl rand -base64 48`.

### Run with Docker

```bash
docker compose up --build
```

- Open http://localhost:3000 and click **Try the demo**.
- MySQL is on port 3307, so it won't clash with a local MySQL.

```bash
docker compose down        # stop, keep the data
docker compose down -v     # stop and wipe the database
```

### Run without Docker

```bash
docker compose up -d db
```

```bash
cd backend
./mvnw spring-boot:run     # .\mvnw.cmd spring-boot:run on Windows
```

```bash
cd frontend
npm install
npm run dev
```

- Open http://localhost:5173.

## Usage

**Log in or try the demo.** The demo has a year of transactions, budgets, bills, two split groups and a fund portfolio. It's deleted after 24 hours.

![Login](docs/images/login.png)

**Transactions.** Search and filter by account, category and date.

![Transactions](docs/images/transactions.png)

**Import a statement.** Pick an account and a CSV, then see what was added or skipped.

![Import](docs/images/import.png)

**Budgets and notifications.** Spending against each limit, with alerts under the bell.

![Budgets](docs/images/budgets.png)

![Notifications](docs/images/notifications.png)

**Bills.** Confirm the detected bills you want reminders for.

![Bills](docs/images/bills.png)

**Split a trip.** See the settle-up plan and everyone's balance.

![A split group](docs/images/split-group.png)

**Add an expense.** Each person's share updates as you type.

![Adding an expense](docs/images/split-expense.png)

**Pay a friend.** Open a UPI link or scan the QR code.

![Paying with UPI](docs/images/split-upi.png)

**Mutual funds.** Value, gain and XIRR per fund and for the portfolio.

![Investments](docs/images/investments.png)

**Record a purchase.** Search for a fund; units come from that day's NAV.

![Recording a purchase](docs/images/investments-add.png)

## API reference

- Every endpoint except `/api/auth/*` needs an `Authorization: Bearer <token>` header.
- Errors follow [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details.
- All amounts are in paise.

**Auth:** `/api/auth/register`, `/login`, `/demo`, `/refresh`, `/logout`, and `GET /api/users/me`

**Ledger:** `/api/accounts`, `/api/categories`, `/api/transactions`, `/api/dashboard`

**Imports:** `/api/imports`, `/api/category-rules`

**Budgets and bills:** `/api/budgets`, `/api/recurring`, `/api/notifications`

**Split groups:** `/api/groups`, with members, expenses, settlements and matches under each group

**Mutual funds:** `/api/investments`, `/api/investments/search`, `/api/investments/transactions`, `/api/investments/sip-links`

Example: adding an expense split by shares.

```http
POST /api/groups/7/expenses
Content-Type: application/json

{
  "description": "Villa in Anjuna, 3 nights",
  "amountPaise": 1800000,
  "date": "2026-09-04",
  "paidByMemberId": 2,
  "splitType": "SHARES",
  "shares": [
    { "memberId": 1, "value": 1 },
    { "memberId": 2, "value": 2 },
    { "memberId": 3, "value": 1 }
  ]
}
```

The response is the updated group, including the new settle-up plan:

```json
{
  "id": 7,
  "name": "Goa trip",
  "settleUp": [
    { "fromName": "Arjun Mehta", "toName": "Rohan Kulkarni", "amountPaise": 450000 },
    { "fromName": "Neha Sharma", "toName": "Rohan Kulkarni", "amountPaise": 450000 }
  ]
}
```

## Testing

```bash
docker compose up -d db
cd backend
./mvnw test                # 164 tests; .\mvnw.cmd test on Windows
```

```bash
cd frontend
npm test                   # 23 tests
```

- Backend tests use a separate `finledger_test` database, so your data is safe.
- Most backend tests go through the real API against MySQL.
- Fund prices come from a fake source, so no test needs the internet.

Screenshots in this README are taken by a script that opens Edge and saves each page to `docs/images`:

```bash
cd frontend
npm run screenshots        # add -- --show to watch it
```

## Project structure

```
backend/src/main/java/com/kaushiksridhar/finledger/
  auth/, security/, user/     login and tokens
  account/, transaction/      the ledger
  importing/, rules/          statement import and categorisation
  budget/, recurring/         budgets and bill detection
  split/                      split groups and settle-up
  investment/                 mutual funds and XIRR
  demo/                       demo data
frontend/src/
  api/                        API calls
  components/                 shared and per-feature UI
  lib/                        money, dates, splits, UPI
  pages/                      one per screen
docker/mysql-init/            creates the test database
```

## Challenges and learnings

- **Alerts broke saves:** an error in a budget check made a saved transaction look failed. Alerts now run in their own transaction.
- **Flaky import tests:** tests read the import before its alerts existed. Alerts now finish before the import is marked done.
- **Real duplicates:** two ₹20 chai payments on one day looked like one row. The fingerprint now counts occurrences.
- **Network calls in transactions:** fetching fund prices mid-transaction held locks. They're now fetched first.
- **Messy bills:** electricity amounts vary a lot, so the detector's tolerance had to be widened.
- **Browser dates:** "Sep" showed as "Sept" in some browsers. Months now come from a fixed list.

## Roadmap

- PDF statement import.
- Email or push notifications.
- Shared groups that friends can edit.
- Capital gains for tax filing.
- A hosted demo.

## License

MIT, see [LICENSE](LICENSE).

## Author

Kaushik Sridhar, [@kaushiksridhar17](https://github.com/kaushiksridhar17)
