-- Requirement 4.4: "A recorded fact is never updated or deleted."
-- This must not depend on every future engineer remembering not to write an
-- UPDATE or DELETE against these tables. The database refuses it outright,
-- regardless of which application, migration author, or ad-hoc psql session
-- attempts it.
--
-- We use a trigger rather than REVOKE UPDATE/DELETE because in most small
-- deployments (Render/Railway/Fly free tiers, this exercise included) the
-- role that runs migrations is the same role the application connects as,
-- which makes a REVOKE trivial to work around by re-GRANTing. A trigger on
-- the table itself cannot be bypassed without dropping the trigger, which is
-- a visible, auditable schema change rather than a quiet permission flip.

CREATE OR REPLACE FUNCTION reject_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Table % is an append-only fact log: % is not permitted (attempted on id=%)',
        TG_TABLE_NAME, TG_OP, COALESCE(OLD.id, NULL)
        USING ERRCODE = '23001'; -- restrict_violation
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sent_transactions_no_update
    BEFORE UPDATE ON sent_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER trg_sent_transactions_no_delete
    BEFORE DELETE ON sent_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER trg_reported_transactions_no_update
    BEFORE UPDATE ON reported_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER trg_reported_transactions_no_delete
    BEFORE DELETE ON reported_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
