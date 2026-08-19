# Reconciliation service

Two sources describe the same money: what **we sent**, and what the **partner
reported**. This service records both as immutable facts and answers, per
reference and per instant, whether they agree — and if not, exactly how they
disagree.

## Running it locally

Requires Docker (for Postgres) and a local JDK 17 + Maven (no wrapper is
committed — `mvn -v` should show 3.8+).

```bash
# 1. Start Postgres
docker run --name reconciliation-db -e POSTGRES_DB=reconciliation \
  -e POSTGRES_USER=reconciliation -e POSTGRES_PASSWORD=reconciliation \
  -p 5432:5432 -d postgres:16

# 2. Run the app (migrations run automatically on startup via Flyway)
mvn spring-boot:run
```

The service starts on `http://localhost:8080`. Seed data (V4 migration) is
loaded automatically — six reference numbers, one per case below.

To run with Docker only:

```bash
docker build -t reconciliation-service .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/reconciliation \
  -e DATABASE_USERNAME=reconciliation -e DATABASE_PASSWORD=reconciliation \
  reconciliation-service
```

## Deploying it

Any host that runs a container and gives you a managed Postgres works —
Render, Railway, Fly.io free tiers all fit. Steps are the same everywhere:

1. Provision a Postgres instance, note its connection URL.
2. Deploy this repo's `Dockerfile` as a web service.
3. Set env vars: `DATABASE_URL` (JDBC form, e.g.
   `jdbc:postgresql://<host>:5432/<db>`), `DATABASE_USERNAME`,
   `DATABASE_PASSWORD`, and `PORT` if the platform requires you to set it
   explicitly (most inject it automatically and the app already reads `PORT`).
4. Migrations run automatically on boot — no separate migration step needed.

**[Deployed address and credentials go here once deployed — see note below.]**

> This project was built in a sandboxed environment without access to Maven
> Central or a deploy target, so it hasn't been built or deployed from here.
> The steps above are exact; running them locally should produce a working
> instance in a few minutes.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/sent-transactions` | Record "we sent this" |
| `GET` | `/api/v1/sent-transactions/{ref}/history` | Every sent fact for a reference, in order |
| `POST` | `/api/v1/reported-transactions` | Record "partner reported this" |
| `GET` | `/api/v1/reported-transactions/{ref}/history` | Every report for a reference, in order |
| `GET` | `/api/v1/reconciliation/{ref}?asOf=<ISO-8601 instant>` | What the system believes about one reference, as of a given instant (default: now) |
| `GET` | `/api/v1/reconciliation?status=<STATUS>&asOf=<instant>` | All references, optionally filtered by status |

Request body for both `POST` endpoints:

```json
{
  "externalReference": "TXN-1001",
  "amountMinorUnits": 250000,
  "currency": "INR",
  "idempotencyKey": "client-generated-unique-key"
}
```

Repeating the same request with the same `idempotencyKey` returns `200` with
the original record instead of creating a duplicate. Reusing the same key
with a different payload returns `409 Conflict`.

`status` values: `MATCHED`, `AMOUNT_MISMATCH`, `CURRENCY_MISMATCH`,
`PENDING_PARTNER_REPORT`, `UNEXPECTED_REPORT`.

## Observing each case

The seed data (`V4__seed_demo_data.sql`) exists specifically so these are
checkable without writing new data first:

```bash
# Clean match
curl localhost:8080/api/v1/reconciliation/TXN-1001

# Amount mismatch (we sent 1000.00, partner reported 950.00)
curl localhost:8080/api/v1/reconciliation/TXN-1002

# Currency mismatch (same numeric amount, different currency)
curl localhost:8080/api/v1/reconciliation/TXN-1003

# Pending - sent but not yet reported
curl localhost:8080/api/v1/reconciliation/TXN-1004

# Unexpected - reported but never sent
curl localhost:8080/api/v1/reconciliation/TXN-1005

# Partner correction over time: query before and after the correction
curl "localhost:8080/api/v1/reconciliation/TXN-1006?asOf=<timestamp before the second report>"
curl "localhost:8080/api/v1/reconciliation/TXN-1006"   # now - after the correction
# See the raw history behind that flip:
curl localhost:8080/api/v1/reported-transactions/TXN-1006/history

# Everything the service currently can't cleanly match
curl "localhost:8080/api/v1/reconciliation?status=AMOUNT_MISMATCH"

# Idempotent retry: same call twice, same idempotencyKey - second call
# returns 200 with the same record, not a duplicate
curl -X POST localhost:8080/api/v1/sent-transactions -H 'Content-Type: application/json' \
  -d '{"externalReference":"TXN-2001","amountMinorUnits":100,"currency":"INR","idempotencyKey":"demo-key-1"}'
curl -X POST localhost:8080/api/v1/sent-transactions -H 'Content-Type: application/json' \
  -d '{"externalReference":"TXN-2001","amountMinorUnits":100,"currency":"INR","idempotencyKey":"demo-key-1"}'

# Reused key, different payload - 409
curl -X POST localhost:8080/api/v1/sent-transactions -H 'Content-Type: application/json' \
  -d '{"externalReference":"TXN-2001","amountMinorUnits":999,"currency":"INR","idempotencyKey":"demo-key-1"}'

# Immutability enforced by the database itself: this fails even though
# nothing in the app code is stopping it
docker exec -it reconciliation-db psql -U reconciliation -d reconciliation \
  -c "UPDATE sent_transactions SET amount_minor_units = 1 WHERE id = 1;"
```

## Design notes

The full reasoning — every case identified, how each was handled or why not,
what's DB-enforced vs. app-enforced, and an honest account of what's not
production ready — is in `NOTES.md`.
