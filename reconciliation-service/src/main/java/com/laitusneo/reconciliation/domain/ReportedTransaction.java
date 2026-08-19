package com.laitusneo.reconciliation.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A fact: "the partner told us this happened." Also append-only. Unlike
 * SentTransaction, more than one row can legitimately share an
 * externalReference — the partner correcting or restating an earlier report
 * is a new fact, not a replacement of the old one (req 4.4). Which report is
 * "current" is a question answered at query time (see ReconciliationService),
 * never by mutating a row.
 */
@Entity
@Table(name = "reported_transactions")
public class ReportedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_reference", nullable = false)
    private String externalReference;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected ReportedTransaction() {
    }

    public ReportedTransaction(String externalReference, Money amount, String idempotencyKey) {
        this.externalReference = externalReference;
        this.amountMinorUnits = amount.amountMinorUnits();
        this.currency = amount.currency();
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() {
        return id;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Money getAmount() {
        return new Money(amountMinorUnits, currency);
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public boolean hasSamePayloadAs(String externalReference, Money amount) {
        return this.externalReference.equals(externalReference) && this.getAmount().equals(amount);
    }
}
