# NOTES

## What I decided the requirements were

Behind "nothing may be recorded as true that cannot be shown to be true," I found: two
sources must stay as separate fact logs rather than merge into one mutable "transaction"
row, because merging them destroys the ability to say which side claimed what; a
correction from either side must be a new fact, never an edit, so the system never loses
the record of what it used to believe; the system must be able to answer what it believed
at a stated past instant even after facts have since changed, which rules out any design
where "current state" is the only thing stored; a currency mismatch has to be checked as
its own condition, not folded into amount comparison, otherwise a currency mix-up with a
numerically equal amount silently passes; there are outcomes other than "match" or
"mismatch" — a reference sent but not yet reported, and a reference reported but never
sent — and calling either of those a mismatch would be a claim the system can't back up;
and the matching rule itself (exact amount and currency, no tolerance) has to be stated
explicitly, because inventing a tolerance is inventing a claim about acceptable loss that
nobody asked for.

Behind "nothing may happen twice because a caller retried," I found: a genuine retry
(same key, same payload) must return the original result, not create a duplicate; a key
reused with a different payload is a different, more concerning case and should be
rejected rather than silently accepted as either the old or new value; and idempotency
has to hold under two requests racing each other, not just two sequential ones, which
means the actual guarantee has to live in the database, not in application logic that
runs once per request.

Cases I identified but did not handle: authentication/authorization on the ingestion
endpoints (anyone who can reach the service can post facts — fine for a 48-hour exercise,
not fine in payments); pagination on the list-everything reconciliation endpoint (it will
return every reference in one response, which doesn't scale); a reconciliation of
reconciliations — i.e., no audit trail of who queried what and when, only of the
underlying facts; multi-currency totals or aggregate reporting; and validation that an
`externalReference` follows any particular format, which I left unconstrained because the
brief doesn't say what business system generates it.

## How I handled each case

Separate `sent_transactions` and `reported_transactions` tables, both append-only, each
row carrying its own `recorded_at`. Matching is computed at query time by picking the
latest fact per reference recorded at or before the requested instant (`DISTINCT ON`
ordered by `recorded_at DESC`), so "as of" queries are just a `WHERE recorded_at <= :asOf`
against real history, not a separate audit mechanism bolted on top. `MatchStatus` has five
values — `MATCHED`, `AMOUNT_MISMATCH`, `CURRENCY_MISMATCH`, `PENDING_PARTNER_REPORT`,
`UNEXPECTED_REPORT` — because I wanted the API to be unable to express "doesn't match"
without saying which of the three genuinely different reasons applies. Currency and
amount are compared as two independent checks so a currency mismatch is never masked by
matching digits.

## Repeat safety

The actual guarantee is a `UNIQUE` constraint on `idempotency_key` in Postgres. The
application does a `SELECT` before `INSERT` as an optimisation — most retries never touch
the constraint at all — but the case that matters is two requests with the same key
arriving close enough together that both pass the `SELECT` check before either commits.
There, the second `INSERT` hits the unique constraint, the app catches that specific
`DataIntegrityViolationException`, re-reads the row the other request just committed, and
returns it. Nothing about correctness depends on the pre-check; it's purely there to avoid
throwing an exception in the common case.

I ruled out two alternatives. `SELECT`-then-`INSERT` with no database constraint: the race
window is real — under load, two identical requests can both read "not present" before
either writes, and both insert. An in-memory lock keyed on the idempotency key: it works
for a single instance in a single process, but a second instance behind a load balancer,
or a restart mid-request, defeats it entirely; the lock has to be visible to every process
that could receive the retry, and the database is the only thing that already is.

## What the database enforces vs. what only the code enforces

The database enforces: no row in either fact table can ever be updated or deleted (a
`BEFORE UPDATE/DELETE` trigger that raises on any attempt, chosen over `REVOKE` because in
a typical small deployment the migration role and the application role are the same, which
makes a bare `REVOKE` trivially reversible); uniqueness of idempotency keys; amounts
non-negative; currency codes matching the three-letter format.

The application enforces: the actual matching rule (exact equality on amount and
currency) — this lives in code because a SQL `CHECK` constraint can't express a
cross-table join condition, and because the rule is exactly the kind of thing likely to
change (a future tolerance band, a rounding rule), so it should be easy to find and change
in one place, not spread across triggers; and rejection of a reused idempotency key with a
different payload — the database's unique constraint stops the *second row*, but deciding
whether the second *request* is a safe retry or a genuine conflict requires comparing
payloads, which needs application logic. For the database to take over that second one, it
would need the request payload to be part of the constraint (e.g. a unique constraint on
`(idempotency_key, external_reference, amount_minor_units, currency)`), which I considered
and rejected because a genuine conflict would then look like a second successful insert
rather than a rejected one — exactly backwards from what I want the caller to see.

## What the service says about a record it can't match

It says one of two specific things, never a bare "no match": `PENDING_PARTNER_REPORT` for
something we sent that hasn't been reported back yet, and `UNEXPECTED_REPORT` for
something reported that we have no record of sending. Both cases still return the record
that does exist, alongside the status. This is the honest answer because the system
genuinely doesn't know whether a pending reference will turn out to match once the partner
reports it, or whether an unexpected report is a partner-side error, a report for
something sent before this system existed, or fraud — collapsing any of those into
"mismatch" would claim a judgment the data doesn't support.

## Where this is not production ready

No authentication on any endpoint. No pagination on the list endpoint. No rate limiting.
The matching rule has no tolerance for currency conversion or fee deduction, which real
partner reports very likely need eventually — I left it exact because inventing a
tolerance without a specified business rule would be a guess dressed up as a decision. The
`idempotency_key` uniqueness is global per table rather than scoped per caller, so two
different legitimate callers who happen to generate the same key would collide — this
should probably be `(caller_id, idempotency_key)` once there's more than one caller. No
metrics or structured logging beyond Spring Boot defaults. And the seed data is small and
hand-picked to demonstrate specific cases, not representative of real volume or the
long tail of cases a live partner feed would eventually produce.
