package com.tullave.recharges.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Envoltura de paginación propia. Evita exponer {@link Page} de Spring Data, cuya serialización
 * JSON no es estable entre versiones y arrastra campos internos (pageable, sort, etc.).
 */
@Schema(description = "Página de resultados")
public record PageResponse<T>(
        List<T> content,
        @Schema(example = "0") int page,
        @Schema(example = "10") int size,
        @Schema(example = "42") long totalElements,
        @Schema(example = "5") int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
