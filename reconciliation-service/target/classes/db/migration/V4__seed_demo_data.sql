-- Seed data. Each block below exists to demonstrate one specific case from
-- NOTES.md. recorded_at is spread across a few hours so that as-of queries
-- have something meaningful to distinguish. See README section "Observing
-- the cases" for the exact requests that exercise each one.

-- Case: clean match. Same reference, same amount, same currency.
INSERT INTO sent_transactions (external_reference, amount_minor_units, currency, idempotency_key, recorded_at) VALUES
    ('TXN-1001', 250000, 'INR', 'seed-sent-1001', now() - interval '6 hours');
INSERT INTO reported_transactions (external_reference, amount_minor_units, currency, idempotency_key, recorded_at) VALUES
    ('TXN-1001', 250000, 'INR', 'seed-reported-1001', now() - interval '5 hours 30 minutes');

-- Case: amount mismatch. We sent ₹1,000.00; partner reports ₹950.00 — a short
-- payment (fee deducted, partial capture, etc). This is deliberately NOT a
-- match, and the service must say so rather than round it into agreement.
INSERT INTO sent_transactions (external_reference, amount_minor_units, currency, idempotency_key, recorded_at) VALUES
    ('TXN-1002', 100000, 'INR', 'seed-sent-1002', now() - interval '4 hours');
INSERT INTO reported_transactions (external_reference, amount_minor_units, currency, idempotency_key, recorded_at) VALUES
    ('TXN-1002', 95000, 'INR', 'seed-reported-1002', now() - interval '3 hours 45 minutes');

-- Case: currency mismatch. Same reference, same numeric amount, different
-- currency — the kind of bug that decimal-blind matching would happily wave
-- through if currency weren't compared explicitly.
INSERT INTO sent_transactions (external_reference, amount_minor_units, currency, idempotency_key, recorded_at) VALUES
    ('TXN-1003', 500000, 'INR', 'seed-sent-1003', now() - interval '3 hours');
INSERT INTO reported_transactions (external_reference, amount_minor_units, currency, idempotency_key, recorded_at) VALUES
    ('TXN-1003', 500000, 'USD', 'seed-reported-1003', now() - interval '2 hours 50 minutes');

-- Case: pending. We sent it; the partner hasn't reported it back yet. This
-- is not a mismatch — it's an honest "don't know yet", and must be labelled
-- differently from a genuine disagreement.
INSERT INTO sent_transactions (external_reference, amount_minor_units, currency, idempotency_key, recorded_at) VALUES
    ('TXN-1004', 120000, 'INR', 'seed-sent-1004', now() - interval '30 minutes');

-- Case: unexpected report. The partner reports a reference we never sent.
-- Could be their error, could be something sent outside this system before
-- it existed — either way we must not silently drop it.
INSERT INTO reported_transactions (external_reference, amount_minor_units, currency, idempotency_key, recorded_at) VALUES
    ('TXN-1005', 75000, 'INR', 'seed-reported-1005', now() - interval '1 hour');

-- Case: partner correction over time. First report is short; a later report
-- for the SAME reference corrects it to match. Both rows survive (4.4) — the
-- "current" belief is the latest report as of a given instant, so an as-of
-- query taken before the correction still truthfully shows a mismatch, and
-- one taken after truthfully shows a match.
INSERT INTO sent_transactions (external_reference, amount_minor_units, currency, idempotency_key, recorded_at) VALUES
    ('TXN-1006', 300000, 'INR', 'seed-sent-1006', now() - interval '5 hours');
INSERT INTO reported_transactions (external_reference, amount_minor_units, currency, idempotency_key, recorded_at) VALUES
    ('TXN-1006', 280000, 'INR', 'seed-reported-1006-a', now() - interval '4 hours 30 minutes'),
    ('TXN-1006', 300000, 'INR', 'seed-reported-1006-b', now() - interval '1 hour');
