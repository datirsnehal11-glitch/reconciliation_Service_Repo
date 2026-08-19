-- reported_transactions: what the PARTNER later told us happened.
-- Also append-only. Critically, a partner can legitimately report the same
-- external_reference more than once over time (their own correction, a restatement,
-- a chargeback reversal, etc). Each report is its own fact; we never overwrite one
-- report with another. The "current belief" for a reference is derived by picking the
-- latest report as of a given instant, not by mutating a row in place.

CREATE TABLE reported_transactions (
    id                  BIGSERIAL PRIMARY KEY,

    external_reference  TEXT NOT NULL,

    amount_minor_units  BIGINT NOT NULL,
    currency            CHAR(3) NOT NULL,

    -- The partner's own idempotency/event key (e.g. their webhook event id).
    -- We rely on THEM for uniqueness of the underlying event, since this
    -- table intentionally allows multiple reports per external_reference.
    idempotency_key     TEXT NOT NULL,

    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_reported_amount_not_negative CHECK (amount_minor_units >= 0),
    CONSTRAINT chk_reported_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT uq_reported_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_reported_external_reference ON reported_transactions (external_reference, recorded_at);
