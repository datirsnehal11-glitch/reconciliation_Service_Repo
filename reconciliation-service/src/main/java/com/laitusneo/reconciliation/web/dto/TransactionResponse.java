package com.laitusneo.reconciliation.web.dto;

import com.laitusneo.reconciliation.domain.ReportedTransaction;
import com.laitusneo.reconciliation.domain.SentTransaction;

import java.time.Instant;

public record TransactionResponse(
        Long id,
        String externalReference,
        long amountMinorUnits,
        String currency,
        String idempotencyKey,
        Instant recordedAt
) {
    public static TransactionResponse from(SentTransaction t) {
        return new TransactionResponse(t.getId(), t.getExternalReference(), t.getAmount().amountMinorUnits(),
                t.getAmount().currency(), t.getIdempotencyKey(), t.getRecordedAt());
    }

    public static TransactionResponse from(ReportedTransaction t) {
        return new TransactionResponse(t.getId(), t.getExternalReference(), t.getAmount().amountMinorUnits(),
                t.getAmount().currency(), t.getIdempotencyKey(), t.getRecordedAt());
    }
}
