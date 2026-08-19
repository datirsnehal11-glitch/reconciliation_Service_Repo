package com.laitusneo.reconciliation.repository;

import com.laitusneo.reconciliation.domain.ReportedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReportedTransactionRepository extends JpaRepository<ReportedTransaction, Long> {

    Optional<ReportedTransaction> findByIdempotencyKey(String idempotencyKey);

    List<ReportedTransaction> findByExternalReferenceOrderByRecordedAtAsc(String externalReference);

    // "Current belief" for a reference = the latest report recorded at or
    // before the queried instant. Earlier reports (corrections superseded
    // later) are not deleted — they simply stop being "latest" once a newer
    // one exists, and only for as-of queries taken after that newer one.
    @Query(value = """
        SELECT DISTINCT ON (external_reference) *
        FROM reported_transactions
        WHERE external_reference = :externalReference AND recorded_at <= :asOf
        ORDER BY external_reference, recorded_at DESC
        """, nativeQuery = true)
    Optional<ReportedTransaction> findLatestAsOf(@Param("externalReference") String externalReference,
                                                  @Param("asOf") Instant asOf);

    @Query(value = """
        SELECT DISTINCT ON (external_reference) *
        FROM reported_transactions
        WHERE recorded_at <= :asOf
        ORDER BY external_reference, recorded_at DESC
        """, nativeQuery = true)
    List<ReportedTransaction> findAllLatestAsOf(@Param("asOf") Instant asOf);
}
