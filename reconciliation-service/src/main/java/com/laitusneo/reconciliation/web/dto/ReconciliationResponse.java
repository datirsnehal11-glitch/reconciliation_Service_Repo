package com.laitusneo.reconciliation.web.dto;

import com.laitusneo.reconciliation.domain.MatchStatus;
import com.laitusneo.reconciliation.service.ReconciliationResult;

import java.time.Instant;

public record ReconciliationResponse(
        String externalReference,
        MatchStatus status,
        Instant asOf,
        TransactionResponse sent,
        TransactionResponse reported
) {
    public static ReconciliationResponse from(ReconciliationResult r) {
        return new ReconciliationResponse(
                r.externalReference(),
                r.status(),
                r.asOf(),
                r.sent() == null ? null : TransactionResponse.from(r.sent()),
                r.reported() == null ? null : TransactionResponse.from(r.reported())
        );
    }
}
