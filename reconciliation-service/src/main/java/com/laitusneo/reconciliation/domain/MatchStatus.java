package com.laitusneo.reconciliation.domain;

/**
 * Every outcome the service is willing to claim about a pair of records.
 * Deliberately more granular than "match / no match" — collapsing
 * AMOUNT_MISMATCH, CURRENCY_MISMATCH, PENDING_PARTNER_REPORT and
 * UNEXPECTED_REPORT into one "doesn't match" bucket would make the service
 * say less than it actually knows, which is its own kind of dishonesty.
 */
public enum MatchStatus {

    /** Both sides recorded, same amount, same currency, as of the queried instant. */
    MATCHED,

    /** Both sides recorded, currencies agree, amounts differ. */
    AMOUNT_MISMATCH,

    /** Both sides recorded, currencies differ. Checked independently of amount, so a currency
     *  mix-up is never masked by amounts happening to be numerically equal. */
    CURRENCY_MISMATCH,

    /** We have a sent record; the partner has not reported anything for this
     *  reference as of the queried instant. Not a disagreement — an honest
     *  "no answer yet." */
    PENDING_PARTNER_REPORT,

    /** The partner reported a reference we have no sent record for, as of the
     *  queried instant. Flagged rather than dropped, since dropping it would
     *  be a claim ("nothing to see here") the service can't actually back up. */
    UNEXPECTED_REPORT
}
