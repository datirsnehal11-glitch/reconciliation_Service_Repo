package com.laitusneo.reconciliation.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Shared request shape for both "we sent this" and "partner reported this."
 * amountMinorUnits is a JSON integer, never a string-encoded decimal — that
 * choice is what keeps a decimal type from ever entering the path, even at
 * the API boundary.
 */
public record RecordTransactionRequest(
        @NotBlank String externalReference,
        @Min(0) long amountMinorUnits,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO 4217 code") String currency,
        @NotBlank String idempotencyKey
) {
}
