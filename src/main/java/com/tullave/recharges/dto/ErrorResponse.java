package com.tullave.recharges.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Formato único de error para toda la API.
 * {@code errors} solo aparece cuando hay detalle por campo (errores de validación).
 */
@Schema(description = "Respuesta de error estándar")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @Schema(example = "2026-10-04T15:30:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "Los datos enviados no son válidos") String message,
        @Schema(example = "/api/v1/recharges") String path,
        List<FieldError> errors
) {

    @Schema(description = "Error de validación de un campo")
    public record FieldError(
            @Schema(example = "cardNumber") String field,
            @Schema(example = "El número de tarjeta debe tener exactamente 16 dígitos numéricos") String message
    ) {
    }
}
