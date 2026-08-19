package com.laitusneo.reconciliation.service;

import com.laitusneo.reconciliation.domain.MatchStatus;
import com.laitusneo.reconciliation.domain.ReportedTransaction;
import com.laitusneo.reconciliation.domain.SentTransaction;
import com.laitusneo.reconciliation.repository.ReportedTransactionRepository;
import com.laitusneo.reconciliation.repository.SentTransactionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes what the system believed about a reference as of a given instant.
 *
 * The matching rule is deliberately simple and stated explicitly, because
 * being able to defend the rule matters more than sophistication: two
 * records match if and only if they share an external_reference and their
 * (amount_minor_units, currency) pairs are equal — a whole-unit, exact
 * comparison, never a tolerance band, since inventing a tolerance would be
 * inventing a claim about acceptable loss that nobody asked this service to make.
 *
 * "As of" is implemented by asking each repository for the latest fact
 * recorded at or before the given instant, per reference — not by filtering
 * a live view. A record's existence is never edited or hidden; only which
 * record counts as "latest" changes with the queried instant.
 */
@Service
public class ReconciliationService {

    private final SentTransactionRepository sentRepository;
    private final ReportedTransactionRepository reportedRepository;

    public ReconciliationService(SentTransactionRepository sentRepository,
                                  ReportedTransactionRepository reportedRepository) {
        this.sentRepository = sentRepository;
        this.reportedRepository = reportedRepository;
    }

    public ReconciliationResult reconcile(String externalReference, Instant asOf) {
        Optional<SentTransaction> sent = sentRepository.findLatestAsOf(externalReference, asOf);
        Optional<ReportedTransaction> reported = reportedRepository.findLatestAsOf(externalReference, asOf);
        return classify(externalReference, asOf, sent.orElse(null), reported.orElse(null));
    }

    /** Every reference either side has ever mentioned, classified as of the given instant. */
    public List<ReconciliationResult> reconcileAll(Instant asOf) {
        List<SentTransaction> allSent = sentRepository.findAllLatestAsOf(asOf);
        List<ReportedTransaction> allReported = reportedRepository.findAllLatestAsOf(asOf);

        var sentByRef = allSent.stream()
                .collect(Collectors.toMap(SentTransaction::getExternalReference, s -> s));
        var reportedByRef = allReported.stream()
                .collect(Collectors.toMap(ReportedTransaction::getExternalReference, r -> r));

        Set<String> allReferences = new HashSet<>();
        allReferences.addAll(sentByRef.keySet());
        allReferences.addAll(reportedByRef.keySet());

        return allReferences.stream()
                .map(ref -> classify(ref, asOf, sentByRef.get(ref), reportedByRef.get(ref)))
                .sorted((a, b) -> a.externalReference().compareTo(b.externalReference()))
                .toList();
    }

    private ReconciliationResult classify(String externalReference, Instant asOf,
                                           SentTransaction sent, ReportedTransaction reported) {
        if (sent == null && reported == null) {
            throw new IllegalStateException("reference known to neither side: " + externalReference);
        }
        if (sent != null && reported == null) {
            return ReconciliationResult.of(externalReference, MatchStatus.PENDING_PARTNER_REPORT, asOf, sent, null);
        }
        if (sent == null) {
            return ReconciliationResult.of(externalReference, MatchStatus.UNEXPECTED_REPORT, asOf, null, reported);
        }

        // Both present: currency is checked independently of amount so a currency
        // mix-up can never be masked by the amounts happening to be numerically equal.
        if (!sent.getAmount().sameCurrencyAs(reported.getAmount())) {
            return ReconciliationResult.of(externalReference, MatchStatus.CURRENCY_MISMATCH, asOf, sent, reported);
        }
        if (!sent.getAmount().sameAmountAs(reported.getAmount())) {
            return ReconciliationResult.of(externalReference, MatchStatus.AMOUNT_MISMATCH, asOf, sent, reported);
        }
        return ReconciliationResult.of(externalReference, MatchStatus.MATCHED, asOf, sent, reported);
    }
}
