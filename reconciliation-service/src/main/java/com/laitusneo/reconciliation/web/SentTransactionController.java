package com.laitusneo.reconciliation.web;

import com.laitusneo.reconciliation.domain.Money;
import com.laitusneo.reconciliation.domain.SentTransaction;
import com.laitusneo.reconciliation.repository.SentTransactionRepository;
import com.laitusneo.reconciliation.service.IngestionService;
import com.laitusneo.reconciliation.web.dto.RecordTransactionRequest;
import com.laitusneo.reconciliation.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Ingests "what we sent" facts. */
@RestController
@RequestMapping("/api/v1/sent-transactions")
public class SentTransactionController {

    private final IngestionService ingestionService;
    private final SentTransactionRepository sentRepository;

    public SentTransactionController(IngestionService ingestionService, SentTransactionRepository sentRepository) {
        this.ingestionService = ingestionService;
        this.sentRepository = sentRepository;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> record(@Valid @RequestBody RecordTransactionRequest request) {
        boolean existedBefore = sentRepository.findByIdempotencyKey(request.idempotencyKey()).isPresent();
        Money amount = new Money(request.amountMinorUnits(), request.currency());
        SentTransaction saved = ingestionService.recordSent(request.externalReference(), amount, request.idempotencyKey());
        HttpStatus status = existedBefore ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransactionResponse.from(saved));
    }

    /** Every sent fact ever recorded for a reference, in order — demonstrates immutability directly. */
    @GetMapping("/{externalReference}/history")
    public List<TransactionResponse> history(@PathVariable String externalReference) {
        return sentRepository.findByExternalReferenceOrderByRecordedAtAsc(externalReference)
                .stream().map(TransactionResponse::from).toList();
    }
}
