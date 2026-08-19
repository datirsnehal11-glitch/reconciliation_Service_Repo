package com.laitusneo.reconciliation.domain;

import java.util.Objects;

/**
 * Money is always an integer count of minor units plus an explicit currency.
 * Never a BigDecimal, never a double, at any point in the path — a mismatch
 * caused by floating point representation error would be a false claim about
 * money, which is the one thing this system is not allowed to be wrong about.
 *
 * Two Money values are only comparable if their currencies match; comparing
 * across currencies is a modelling error the caller must not be able to make
 * accidentally, so equals() treats different currencies as simply unequal
 * rather than throwing, and callers that need to explain *why* two amounts
 * differ do currency and amount comparison separately (see ReconciliationService).
 */
public final class Money {

    private final long amountMinorUnits;
    private final String currency;

    public Money(long amountMinorUnits, String currency) {
        if (amountMinorUnits < 0) {
            throw new IllegalArgumentException("amountMinorUnits must not be negative: " + amountMinorUnits);
        }
        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code: " + currency);
        }
        this.amountMinorUnits = amountMinorUnits;
        this.currency = currency;
    }

    public long amountMinorUnits() {
        return amountMinorUnits;
    }

    public String currency() {
        return currency;
    }

    public boolean sameCurrencyAs(Money other) {
        return this.currency.equals(other.currency);
    }

    public boolean sameAmountAs(Money other) {
        return this.amountMinorUnits == other.amountMinorUnits;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amountMinorUnits == money.amountMinorUnits && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountMinorUnits, currency);
    }

    @Override
    public String toString() {
        return amountMinorUnits + " " + currency;
    }
}
