package com.laitusneo.reconciliation.web;

import com.laitusneo.reconciliation.domain.MatchStatus;
import com.laitusneo.reconciliation.service.ReconciliationService;
import com.laitusneo.reconciliation.web.dto.ReconciliationResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * What the system believes about one reference, as of a given instant
     * (default: now). asOf is honoured literally — facts recorded after it
     * are invisible to this query, even if they exist in the table by the
     * time the request is handled (req 4.5).
     */
    @GetMapping("/{externalReference}")
    public ReconciliationResponse reconcileOne(
            @PathVariable String externalReference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf) {
        Instant effectiveAsOf = asOf != null ? asOf : Instant.now();
        return ReconciliationResponse.from(reconciliationService.reconcile(externalReference, effectiveAsOf));
    }

    /**
     * Every reference either side has ever mentioned, classified as of the
     * given instant, optionally filtered to one status. This is the view
     * for "what can't you match" — nothing that doesn't cleanly match is
     * hidden from it.
     */
    @GetMapping
    public List<ReconciliationResponse> reconcileAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf,
            @RequestParam(required = false) MatchStatus status) {
        Instant effectiveAsOf = asOf != null ? asOf : Instant.now();
        return reconciliationService.reconcileAll(effectiveAsOf).stream()
                .filter(r -> status == null || r.status() == status)
                .map(ReconciliationResponse::from)
                .toList();
    }
}
