package com.tullave.recharges.dto;

import com.tullave.recharges.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Payload de entrada para crear una recarga. Es un record inmutable: la entidad JPA nunca se expone.
 */
@Schema(description = "Datos para registrar una recarga")
public record RechargeRequest(

        @Schema(description = "Número de tarjeta tuLlave (exactamente 16 dígitos)", example = "1010000012345678")
        @NotBlank(message = "El número de tarjeta es obligatorio")
        @Pattern(regexp = "^\\d{16}$", message = "El número de tarjeta debe tener exactamente 16 dígitos numéricos")
        String cardNumber,

        @Schema(description = "Monto a recargar en COP", example = "50000", minimum = "2000", maximum = "200000")
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "2000", message = "El monto mínimo de recarga es 2.000")
        @DecimalMax(value = "200000", message = "El monto máximo de recarga es 200.000")
        @Digits(integer = 10, fraction = 2, message = "El monto admite máximo 2 decimales")
        BigDecimal amount,

        @Schema(description = "Medio de pago", example = "NEQUI")
        @NotNull(message = "El medio de pago es obligatorio (PSE, NEQUI, DAVIPLATA, CREDIT_CARD)")
        PaymentMethod paymentMethod
) {
}
