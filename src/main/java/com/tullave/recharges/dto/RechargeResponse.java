package com.tullave.recharges.dto;

import com.tullave.recharges.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Recarga registrada")
public record RechargeResponse(
        @Schema(example = "1") Long id,
        @Schema(description = "Referencia pública de la transacción", example = "6f1c2a0e-3b5d-4f7a-9c1e-2d3e4f5a6b7c") UUID reference,
        @Schema(example = "1010000012345678") String cardNumber,
        @Schema(example = "50000.00") BigDecimal amount,
        @Schema(example = "NEQUI") PaymentMethod paymentMethod,
        @Schema(example = "2026-10-04T15:30:00Z") Instant createdAt
) {
}
