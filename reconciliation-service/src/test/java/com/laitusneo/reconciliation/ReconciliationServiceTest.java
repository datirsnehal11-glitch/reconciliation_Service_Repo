package com.laitusneo.reconciliation;

import com.laitusneo.reconciliation.domain.*;
import com.laitusneo.reconciliation.repository.ReportedTransactionRepository;
import com.laitusneo.reconciliation.repository.SentTransactionRepository;
import com.laitusneo.reconciliation.service.ReconciliationResult;
import com.laitusneo.reconciliation.service.ReconciliationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconciliationServiceTest {

    private final SentTransactionRepository sentRepo = mock(SentTransactionRepository.class);
    private final ReportedTransactionRepository reportedRepo = mock(ReportedTransactionRepository.class);
    private final ReconciliationService service = new ReconciliationService(sentRepo, reportedRepo);
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private SentTransaction sent(String ref, long amount, String ccy) throws Exception {
        return construct(SentTransaction.class, ref, new Money(amount, ccy));
    }

    private ReportedTransaction reported(String ref, long amount, String ccy) throws Exception {
        return construct(ReportedTransaction.class, ref, new Money(amount, ccy));
    }

    @SuppressWarnings("unchecked")
    private <T> T construct(Class<T> type, String ref, Money amount) throws Exception {
        var ctor = type.getDeclaredConstructor(String.class, Money.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(ref, amount, "test-key-" + ref);
    }

    @Test
    void sameAmountAndCurrency_isMatched() throws Exception {
        when(sentRepo.findLatestAsOf("REF1", now)).thenReturn(Optional.of(sent("REF1", 1000, "INR")));
        when(reportedRepo.findLatestAsOf("REF1", now)).thenReturn(Optional.of(reported("REF1", 1000, "INR")));

        ReconciliationResult result = service.reconcile("REF1", now);

        assertEquals(MatchStatus.MATCHED, result.status());
    }

    @Test
    void sameCurrencyDifferentAmount_isAmountMismatch() throws Exception {
        when(sentRepo.findLatestAsOf("REF2", now)).thenReturn(Optional.of(sent("REF2", 1000, "INR")));
        when(reportedRepo.findLatestAsOf("REF2", now)).thenReturn(Optional.of(reported("REF2", 950, "INR")));

        assertEquals(MatchStatus.AMOUNT_MISMATCH, service.reconcile("REF2", now).status());
    }

    @Test
    void sameAmountDifferentCurrency_isCurrencyMismatch_notMatched() throws Exception {
        // Regression guard: a naive numeric-only comparison would wrongly call this a match.
        when(sentRepo.findLatestAsOf("REF3", now)).thenReturn(Optional.of(sent("REF3", 5000, "INR")));
        when(reportedRepo.findLatestAsOf("REF3", now)).thenReturn(Optional.of(reported("REF3", 5000, "USD")));

        assertEquals(MatchStatus.CURRENCY_MISMATCH, service.reconcile("REF3", now).status());
    }

    @Test
    void sentOnly_isPending_notMismatch() throws Exception {
        when(sentRepo.findLatestAsOf("REF4", now)).thenReturn(Optional.of(sent("REF4", 1000, "INR")));
        when(reportedRepo.findLatestAsOf("REF4", now)).thenReturn(Optional.empty());

        assertEquals(MatchStatus.PENDING_PARTNER_REPORT, service.reconcile("REF4", now).status());
    }

    @Test
    void reportedOnly_isUnexpected_notSilentlyDropped() throws Exception {
        when(sentRepo.findLatestAsOf("REF5", now)).thenReturn(Optional.empty());
        when(reportedRepo.findLatestAsOf("REF5", now)).thenReturn(Optional.of(reported("REF5", 1000, "INR")));

        assertEquals(MatchStatus.UNEXPECTED_REPORT, service.reconcile("REF5", now).status());
    }
}
