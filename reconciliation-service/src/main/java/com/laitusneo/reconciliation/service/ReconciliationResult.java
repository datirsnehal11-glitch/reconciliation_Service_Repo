package com.laitusneo.reconciliation.service;

import com.laitusneo.reconciliation.domain.MatchStatus;
import com.laitusneo.reconciliation.domain.ReportedTransaction;
import com.laitusneo.reconciliation.domain.SentTransaction;

import java.time.Instant;

/**
 * What the service is willing to say about one external_reference as of one
 * instant. sent/reported are the specific facts the status was computed
 * from — included so the caller can see exactly why, not just the verdict.
 */
public record ReconciliationResult(
        String externalReference,
        MatchStatus status,
        Instant asOf,
        SentTransaction sent,
        ReportedTransaction reported
) {
    public static ReconciliationResult of(String externalReference, MatchStatus status, Instant asOf,
                                           SentTransaction sent, ReportedTransaction reported) {
        return new ReconciliationResult(externalReference, status, asOf, sent, reported);
    }
}
