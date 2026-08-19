package com.laitusneo.reconciliation;

import com.laitusneo.reconciliation.domain.Money;
import com.laitusneo.reconciliation.domain.SentTransaction;
import com.laitusneo.reconciliation.repository.ReportedTransactionRepository;
import com.laitusneo.reconciliation.repository.SentTransactionRepository;
import com.laitusneo.reconciliation.service.IdempotencyConflictException;
import com.laitusneo.reconciliation.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestionServiceTest {

    private final SentTransactionRepository sentRepo = mock(SentTransactionRepository.class);
    private final ReportedTransactionRepository reportedRepo = mock(ReportedTransactionRepository.class);
    private final IngestionService service = new IngestionService(sentRepo, reportedRepo);

    @Test
    void sameKeySamePayload_isTreatedAsRetry_noNewRowCreated() {
        Money amount = new Money(1000, "INR");
        SentTransaction existing = new SentTransaction("REF1", amount, "key-1");
        when(sentRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        SentTransaction result = service.recordSent("REF1", amount, "key-1");

        assertSame(existing, result);
        verify(sentRepo, never()).save(any());
    }

    @Test
    void sameKeyDifferentPayload_isRejected_notSilentlyOverwritten() {
        Money original = new Money(1000, "INR");
        SentTransaction existing = new SentTransaction("REF1", original, "key-1");
        when(sentRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Money differentAmount = new Money(2000, "INR");

        assertThrows(IdempotencyConflictException.class,
                () -> service.recordSent("REF1", differentAmount, "key-1"));
        verify(sentRepo, never()).save(any());
    }

    @Test
    void concurrentRetry_raceLostOnInsert_stillReturnsOriginalNotAnError() {
        // Simulates two requests with the same key arriving together: this thread's
        // pre-check finds nothing, but by the time it INSERTs, the other thread has
        // already committed the row, so the DB's unique constraint rejects the insert.
        Money amount = new Money(1000, "INR");
        SentTransaction winner = new SentTransaction("REF1", amount, "key-1");

        when(sentRepo.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty())   // pre-check: nothing yet
                .thenReturn(Optional.of(winner)); // re-check after the race: the other request won
        when(sentRepo.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        SentTransaction result = service.recordSent("REF1", amount, "key-1");

        assertSame(winner, result);
    }
}
