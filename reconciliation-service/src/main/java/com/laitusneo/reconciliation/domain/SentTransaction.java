package com.laitusneo.reconciliation.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A fact: "we sent this." Append-only — see V3 migration, which makes the
 * database itself refuse UPDATE/DELETE against this table regardless of what
 * this class does. This entity therefore has no setters; that's a reminder
 * to the next engineer, not the actual enforcement mechanism.
 */
@Entity
@Table(name = "sent_transactions")
public class SentTransaction {

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

    protected SentTransaction() {
        // required by JPA
    }

    public SentTransaction(String externalReference, Money amount, String idempotencyKey) {
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

    /** True if this row's payload matches the given proposed re-send — used to tell a genuine retry from a reused key with different content. */
    public boolean hasSamePayloadAs(String externalReference, Money amount) {
        return this.externalReference.equals(externalReference) && this.getAmount().equals(amount);
    }
}
