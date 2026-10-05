package com.tullave.recharges.controller;

import com.tullave.recharges.dto.ErrorResponse;
import com.tullave.recharges.dto.PageResponse;
import com.tullave.recharges.dto.RechargeRequest;
import com.tullave.recharges.dto.RechargeResponse;
import com.tullave.recharges.service.RechargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Recargas", description = "Registro y consulta de recargas digitales de la tarjeta tuLlave")
public class RechargeController {

    private static final int MAX_PAGE_SIZE = 100;

    private final RechargeService service;

    public RechargeController(RechargeService service) {
        this.service = service;
    }

    @PostMapping("/recharges")
    @Operation(summary = "Registrar una recarga")
    @ApiResponse(responseCode = "201", description = "Recarga creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<RechargeResponse> create(@Valid @RequestBody RechargeRequest request) {
        RechargeResponse created = service.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/recharges/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /**
     * La ruta {@code /getRecharges} es la exigida por la especificación; {@code /recharges} se expone
     * además como alias REST convencional. Ambas comparten la misma lógica.
     */
    @GetMapping({"/getRecharges", "/recharges"})
    @Operation(summary = "Listar recargas con paginación y filtro opcional por número de tarjeta")
    @ApiResponse(responseCode = "200", description = "Página de recargas")
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PageResponse<RechargeResponse>> list(
            @Parameter(description = "Número de tarjeta (16 dígitos) para filtrar")
            @RequestParam(required = false)
            @Pattern(regexp = "^\\d{16}$", message = "El número de tarjeta debe tener exactamente 16 dígitos numéricos")
            String cardNumber,

            @Parameter(description = "Número de página (desde 0)")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "La página no puede ser negativa")
            int page,

            @Parameter(description = "Tamaño de página (1 a 100)")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "El tamaño de página mínimo es 1")
            @Max(value = MAX_PAGE_SIZE, message = "El tamaño de página máximo es " + MAX_PAGE_SIZE)
            int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return ResponseEntity.ok(service.findAll(cardNumber, pageable));
    }

    @GetMapping("/recharges/{id}")
    @Operation(summary = "Consultar una recarga por id")
    @ApiResponse(responseCode = "200", description = "Recarga encontrada")
    @ApiResponse(responseCode = "404", description = "No existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<RechargeResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @DeleteMapping("/recharges/{id}")
    @Operation(summary = "Eliminar una recarga")
    @ApiResponse(responseCode = "204", description = "Recarga eliminada")
    @ApiResponse(responseCode = "404", description = "No existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
