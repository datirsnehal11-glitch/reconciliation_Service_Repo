package com.laitusneo.reconciliation.repository;

import com.laitusneo.reconciliation.domain.SentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SentTransactionRepository extends JpaRepository<SentTransaction, Long> {

    Optional<SentTransaction> findByIdempotencyKey(String idempotencyKey);

    List<SentTransaction> findByExternalReferenceOrderByRecordedAtAsc(String externalReference);

    // A given external_reference is only expected to have one sent fact in the
    // ordinary case, but nothing in the schema forbids more than one (e.g. a
    // correction to what we believed we sent). "As of" therefore means: the
    // latest sent fact recorded at or before the given instant.
    @Query(value = """
        SELECT DISTINCT ON (external_reference) *
        FROM sent_transactions
        WHERE external_reference = :externalReference AND recorded_at <= :asOf
        ORDER BY external_reference, recorded_at DESC
        """, nativeQuery = true)
    Optional<SentTransaction> findLatestAsOf(@Param("externalReference") String externalReference,
                                              @Param("asOf") Instant asOf);

    @Query(value = """
        SELECT DISTINCT ON (external_reference) *
        FROM sent_transactions
        WHERE recorded_at <= :asOf
        ORDER BY external_reference, recorded_at DESC
        """, nativeQuery = true)
    List<SentTransaction> findAllLatestAsOf(@Param("asOf") Instant asOf);
}
