package com.laitusneo.reconciliation.service;

/**
 * Thrown when a caller reuses an idempotency key with a DIFFERENT payload
 * than the one originally stored under that key. This is deliberately not
 * the same code path as a genuine retry (same key, same payload), which is
 * handled silently by returning the original record. A reused key with
 * different content usually means a caller bug (key generation collision,
 * a client replaying a template with new data) and is surfaced rather than
 * silently accepted or silently ignored.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
