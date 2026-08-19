package com.laitusneo.reconciliation.service;

import com.laitusneo.reconciliation.domain.Money;
import com.laitusneo.reconciliation.domain.ReportedTransaction;
import com.laitusneo.reconciliation.domain.SentTransaction;
import com.laitusneo.reconciliation.repository.ReportedTransactionRepository;
import com.laitusneo.reconciliation.repository.SentTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Handles writes into the two fact tables, making retries safe.
 *
 * The safety net is the database's UNIQUE constraint on idempotency_key
 * (V1/V2 migrations), not this code. This class does a read-before-write
 * as an optimisation (so a normal retry doesn't even have to hit a
 * constraint violation), but the actual guarantee under concurrent retries
 * — two requests with the same key arriving at nearly the same instant —
 * comes from catching the DB's rejection of the second INSERT and treating
 * it as "someone already recorded this," not from any locking done here.
 *
 * Alternatives considered and rejected — see NOTES.md for the full
 * reasoning, but briefly:
 *   - SELECT-then-INSERT with no DB constraint: has a race window between
 *     the SELECT and the INSERT under concurrent requests; two identical
 *     requests arriving together can both pass the SELECT check.
 *   - Application-level in-memory lock keyed on idempotency_key: doesn't
 *     survive a restart or a second instance of the service, which defeats
 *     the point once this runs behind a load balancer.
 */
@Service
public class IngestionService {

    private final SentTransactionRepository sentRepository;
    private final ReportedTransactionRepository reportedRepository;

    public IngestionService(SentTransactionRepository sentRepository,
                             ReportedTransactionRepository reportedRepository) {
        this.sentRepository = sentRepository;
        this.reportedRepository = reportedRepository;
    }

    public SentTransaction recordSent(String externalReference, Money amount, String idempotencyKey) {
        var existing = sentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), externalReference, amount);
        }
        try {
            return sentRepository.save(new SentTransaction(externalReference, amount, idempotencyKey));
        } catch (DataIntegrityViolationException race) {
            // Another request with the same key won the race between our SELECT and this INSERT.
            SentTransaction winner = sentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> race);
            return replayOrConflict(winner, externalReference, amount);
        }
    }

    public ReportedTransaction recordReported(String externalReference, Money amount, String idempotencyKey) {
        var existing = reportedRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), externalReference, amount);
        }
        try {
            return reportedRepository.save(new ReportedTransaction(externalReference, amount, idempotencyKey));
        } catch (DataIntegrityViolationException race) {
            ReportedTransaction winner = reportedRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> race);
            return replayOrConflict(winner, externalReference, amount);
        }
    }

    private SentTransaction replayOrConflict(SentTransaction existing, String externalReference, Money amount) {
        if (existing.hasSamePayloadAs(externalReference, amount)) {
            return existing; // genuine retry — return the original fact, create nothing new
        }
        throw new IdempotencyConflictException(
                "idempotency key already used with a different payload for reference " + externalReference);
    }

    private ReportedTransaction replayOrConflict(ReportedTransaction existing, String externalReference, Money amount) {
        if (existing.hasSamePayloadAs(externalReference, amount)) {
            return existing;
        }
        throw new IdempotencyConflictException(
                "idempotency key already used with a different payload for reference " + externalReference);
    }
}
