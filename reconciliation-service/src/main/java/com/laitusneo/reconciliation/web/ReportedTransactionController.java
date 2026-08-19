package com.laitusneo.reconciliation.web;

import com.laitusneo.reconciliation.domain.Money;
import com.laitusneo.reconciliation.domain.ReportedTransaction;
import com.laitusneo.reconciliation.repository.ReportedTransactionRepository;
import com.laitusneo.reconciliation.service.IngestionService;
import com.laitusneo.reconciliation.web.dto.RecordTransactionRequest;
import com.laitusneo.reconciliation.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Ingests "what the partner reported" facts. Unlike sent-transactions, more
 *  than one of these can legitimately exist per reference (a correction). */
@RestController
@RequestMapping("/api/v1/reported-transactions")
public class ReportedTransactionController {

    private final IngestionService ingestionService;
    private final ReportedTransactionRepository reportedRepository;

    public ReportedTransactionController(IngestionService ingestionService,
                                          ReportedTransactionRepository reportedRepository) {
        this.ingestionService = ingestionService;
        this.reportedRepository = reportedRepository;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> record(@Valid @RequestBody RecordTransactionRequest request) {
        boolean existedBefore = reportedRepository.findByIdempotencyKey(request.idempotencyKey()).isPresent();
        Money amount = new Money(request.amountMinorUnits(), request.currency());
        ReportedTransaction saved = ingestionService.recordReported(request.externalReference(), amount, request.idempotencyKey());
        HttpStatus status = existedBefore ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransactionResponse.from(saved));
    }

    /** Every report ever received for a reference, in order — this is where a
     *  partner correction becomes visible: two rows, same reference, both kept. */
    @GetMapping("/{externalReference}/history")
    public List<TransactionResponse> history(@PathVariable String externalReference) {
        return reportedRepository.findByExternalReferenceOrderByRecordedAtAsc(externalReference)
                .stream().map(TransactionResponse::from).toList();
    }
}
