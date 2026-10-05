package com.tullave.recharges.controller;

import com.tullave.recharges.dto.PageResponse;
import com.tullave.recharges.dto.RechargeRequest;
import com.tullave.recharges.dto.RechargeResponse;
import com.tullave.recharges.exception.GlobalExceptionHandler;
import com.tullave.recharges.exception.ResourceNotFoundException;
import com.tullave.recharges.model.PaymentMethod;
import com.tullave.recharges.service.RechargeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la capa web: mapeo de rutas, códigos HTTP, validación de entrada y formato de error.
 * El servicio se simula; la lógica de negocio se prueba en {@code RechargeServiceImplTest}.
 */
@WebMvcTest(RechargeController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("RechargeController")
class RechargeControllerTest {

    private static final String CARD = "1010000012345678";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RechargeService service;

    private static RechargeResponse sample(Long id) {
        return new RechargeResponse(id, UUID.randomUUID(), CARD, new BigDecimal("50000.00"),
                PaymentMethod.NEQUI, Instant.parse("2026-10-04T12:00:00Z"));
    }

    @Nested
    @DisplayName("POST /api/v1/recharges")
    class Create {

        @Test
        @DisplayName("201 Created con Location y cuerpo de la recarga")
        void createsRecharge() throws Exception {
            when(service.create(any(RechargeRequest.class))).thenReturn(sample(1L));

            mockMvc.perform(post("/api/v1/recharges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"cardNumber": "1010000012345678", "amount": 50000, "paymentMethod": "NEQUI"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/api/v1/recharges/1"))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.reference").isNotEmpty())
                    .andExpect(jsonPath("$.cardNumber").value(CARD))
                    .andExpect(jsonPath("$.amount").value(50000.00))
                    .andExpect(jsonPath("$.paymentMethod").value("NEQUI"))
                    .andExpect(jsonPath("$.createdAt").value("2026-10-04T12:00:00Z"));
        }

        @Test
        @DisplayName("400 con detalle por campo cuando el número de tarjeta no tiene 16 dígitos")
        void rejectsInvalidCardNumber() throws Exception {
            mockMvc.perform(post("/api/v1/recharges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"cardNumber": "12345", "amount": 50000, "paymentMethod": "NEQUI"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Los datos enviados no son válidos"))
                    .andExpect(jsonPath("$.path").value("/api/v1/recharges"))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.errors", hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].field").value("cardNumber"));

            verify(service, never()).create(any());
        }

        @Test
        @DisplayName("400 cuando el monto está fuera del rango permitido")
        void rejectsAmountOutOfRange() throws Exception {
            mockMvc.perform(post("/api/v1/recharges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"cardNumber": "1010000012345678", "amount": 1999, "paymentMethod": "PSE"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("amount"))
                    .andExpect(jsonPath("$.errors[0].message").value("El monto mínimo de recarga es 2.000"));

            mockMvc.perform(post("/api/v1/recharges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"cardNumber": "1010000012345678", "amount": 200000.01, "paymentMethod": "PSE"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("amount"))
                    .andExpect(jsonPath("$.errors[0].message").value("El monto máximo de recarga es 200.000"));
        }

        @Test
        @DisplayName("400 con todos los campos faltantes reportados a la vez")
        void reportsAllMissingFields() throws Exception {
            mockMvc.perform(post("/api/v1/recharges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasSize(3)))
                    .andExpect(jsonPath("$.errors[0].field").value("amount"))
                    .andExpect(jsonPath("$.errors[1].field").value("cardNumber"))
                    .andExpect(jsonPath("$.errors[2].field").value("paymentMethod"));
        }

        @Test
        @DisplayName("400 cuando el medio de pago no es un valor del enum")
        void rejectsUnknownPaymentMethod() throws Exception {
            mockMvc.perform(post("/api/v1/recharges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"cardNumber": "1010000012345678", "amount": 50000, "paymentMethod": "EFECTIVO"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("paymentMethod"))
                    .andExpect(jsonPath("$.errors[0].message").value(
                            "Valor no permitido. Valores válidos: [PSE, NEQUI, DAVIPLATA, CREDIT_CARD]"));
        }

        @Test
        @DisplayName("400 cuando el JSON está mal formado")
        void rejectsMalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/recharges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cardNumber\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud es inválido o está mal formado"))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/getRecharges")
    class List_ {

        @Test
        @DisplayName("200 con página por defecto (page=0, size=10) ordenada por fecha descendente")
        void listsWithDefaults() throws Exception {
            when(service.findAll(isNull(), any(Pageable.class)))
                    .thenReturn(new PageResponse<>(List.of(sample(2L), sample(1L)), 0, 10, 2, 1, true, true));

            mockMvc.perform(get("/api/v1/getRecharges"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].id").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(true));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(service).findAll(isNull(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isZero();
            assertThat(captor.getValue().getPageSize()).isEqualTo(10);
            assertThat(captor.getValue().getSort().getOrderFor("createdAt"))
                    .isNotNull()
                    .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("pasa el filtro por tarjeta y la paginación solicitada al servicio")
        void passesFilterAndPaging() throws Exception {
            when(service.findAll(eq(CARD), any(Pageable.class)))
                    .thenReturn(new PageResponse<>(List.of(sample(1L)), 2, 5, 11, 3, false, true));

            mockMvc.perform(get("/api/v1/getRecharges")
                            .param("cardNumber", CARD)
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.size").value(5))
                    .andExpect(jsonPath("$.totalElements").value(11));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(service).findAll(eq(CARD), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("el alias REST /api/v1/recharges responde igual")
        void restAliasWorks() throws Exception {
            when(service.findAll(isNull(), any(Pageable.class)))
                    .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true));

            mockMvc.perform(get("/api/v1/recharges"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("400 cuando el filtro de tarjeta no tiene 16 dígitos")
        void rejectsInvalidCardFilter() throws Exception {
            mockMvc.perform(get("/api/v1/getRecharges").param("cardNumber", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Los parámetros enviados no son válidos"))
                    .andExpect(jsonPath("$.errors[0].field").value("cardNumber"));

            verify(service, never()).findAll(any(), any());
        }

        @Test
        @DisplayName("400 cuando page es negativo o size excede el máximo")
        void rejectsInvalidPaging() throws Exception {
            mockMvc.perform(get("/api/v1/getRecharges").param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("page"));

            mockMvc.perform(get("/api/v1/getRecharges").param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("size"))
                    .andExpect(jsonPath("$.errors[0].message").value("El tamaño de página máximo es 100"));
        }

        @Test
        @DisplayName("400 cuando page no es numérico")
        void rejectsNonNumericPage() throws Exception {
            mockMvc.perform(get("/api/v1/getRecharges").param("page", "uno"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("page"))
                    .andExpect(jsonPath("$.errors[0].message").value("Se esperaba un valor de tipo int"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/recharges/{id}")
    class FindById {

        @Test
        @DisplayName("200 cuando existe")
        void returnsRecharge() throws Exception {
            when(service.findById(3L)).thenReturn(sample(3L));

            mockMvc.perform(get("/api/v1/recharges/3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(3));
        }

        @Test
        @DisplayName("404 cuando no existe")
        void notFound() throws Exception {
            when(service.findById(99L)).thenThrow(ResourceNotFoundException.recharge(99L));

            mockMvc.perform(get("/api/v1/recharges/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("No existe una recarga con id 99"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/recharges/{id}")
    class Delete {

        @Test
        @DisplayName("204 No Content cuando se elimina")
        void deletes() throws Exception {
            mockMvc.perform(delete("/api/v1/recharges/1"))
                    .andExpect(status().isNoContent());

            verify(service).delete(1L);
        }

        @Test
        @DisplayName("404 con formato de error estándar cuando no existe")
        void notFound() throws Exception {
            doThrow(ResourceNotFoundException.recharge(99L)).when(service).delete(99L);

            mockMvc.perform(delete("/api/v1/recharges/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("No existe una recarga con id 99"))
                    .andExpect(jsonPath("$.path").value("/api/v1/recharges/99"));
        }

        @Test
        @DisplayName("400 cuando el id no es numérico")
        void rejectsNonNumericId() throws Exception {
            mockMvc.perform(delete("/api/v1/recharges/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("id"));
        }
    }

    @Nested
    @DisplayName("Errores genéricos")
    class Generic {

        @Test
        @DisplayName("404 con formato estándar para rutas inexistentes")
        void unknownRoute() throws Exception {
            mockMvc.perform(get("/api/v1/unknown"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("La ruta solicitada no existe"));
        }

        @Test
        @DisplayName("405 con formato estándar para métodos no soportados")
        void methodNotAllowed() throws Exception {
            mockMvc.perform(post("/api/v1/recharges/1"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value(405));
        }

        @Test
        @DisplayName("500 sin filtrar detalles internos cuando ocurre un error inesperado")
        void unexpectedError() throws Exception {
            when(service.findById(1L)).thenThrow(new IllegalStateException("fallo interno con detalles sensibles"));

            mockMvc.perform(get("/api/v1/recharges/1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Ocurrió un error inesperado"));
        }
    }
}
