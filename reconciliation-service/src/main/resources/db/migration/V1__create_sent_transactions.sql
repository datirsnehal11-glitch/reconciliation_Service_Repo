-- sent_transactions: what WE sent to the partner.
-- This is an append-only fact log. A row is never updated or deleted (enforced in V3).
-- If we ever need to correct what we believe we sent, that correction is a NEW row,
-- not an edit of this one — the original stays as evidence of what was true at the time.

CREATE TABLE sent_transactions (
    id                  BIGSERIAL PRIMARY KEY,

    -- The business key that ties a sent record to its eventual partner report.
    -- Chosen by us at send time (e.g. our payment/order reference).
    external_reference  TEXT NOT NULL,

    -- Money is never a decimal or float. Integer minor units + an explicit currency.
    amount_minor_units  BIGINT NOT NULL,
    currency            CHAR(3) NOT NULL,

    -- Caller-supplied key that makes re-sending the same request safe.
    -- Unique per row: a genuine retry must map to exactly one stored fact.
    idempotency_key     TEXT NOT NULL,

    -- When THIS SYSTEM recorded the fact — not when the money moved.
    -- This is the timestamp "as-of" queries are measured against (req 4.5).
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_sent_amount_not_negative CHECK (amount_minor_units >= 0),
    CONSTRAINT chk_sent_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT uq_sent_idempotency_key UNIQUE (idempotency_key)
);

-- We look up "what did we send for reference X" and "what's the timeline for X" constantly.
CREATE INDEX idx_sent_external_reference ON sent_transactions (external_reference, recorded_at);
