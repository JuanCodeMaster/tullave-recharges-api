package com.tullave.recharges.integration;

import com.tullave.recharges.repository.RechargeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de extremo a extremo contra un PostgreSQL real (Testcontainers):
 * levanta el contexto completo, aplica las migraciones de Flyway y recorre el flujo
 * crear -> listar/filtrar -> consultar -> eliminar.
 * <p>
 * Requiere Docker. Se ejecuta con {@code ./mvnw verify -Pit}.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("API de recargas (integración con PostgreSQL)")
class RechargeApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String CARD_A = "1010000012345678";
    private static final String CARD_B = "2020000087654321";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RechargeRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private MvcResult create(String card, String amount, String method) throws Exception {
        return mockMvc.perform(post("/api/v1/recharges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\": \"%s\", \"amount\": %s, \"paymentMethod\": \"%s\"}"
                                .formatted(card, amount, method)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    @DisplayName("flujo completo: crear, listar con filtro, consultar y eliminar")
    void fullLifecycle() throws Exception {
        MvcResult created = create(CARD_A, "50000", "NEQUI");
        String location = created.getResponse().getHeader("Location");
        assertThat(location).isNotNull().contains("/api/v1/recharges/");

        create(CARD_A, "2000", "PSE");
        create(CARD_B, "200000", "CREDIT_CARD");

        assertThat(repository.count()).isEqualTo(3);

        // Listado general ordenado por fecha descendente (el último creado va primero)
        mockMvc.perform(get("/api/v1/getRecharges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].cardNumber").value(CARD_B));

        // Filtro por tarjeta y paginación
        mockMvc.perform(get("/api/v1/getRecharges").param("cardNumber", CARD_A).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].cardNumber").value(CARD_A))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        // Consulta por id usando el Location devuelto
        String path = location.substring(location.indexOf("/api/v1"));
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value(CARD_A))
                .andExpect(jsonPath("$.amount").value(50000.00))
                .andExpect(jsonPath("$.paymentMethod").value("NEQUI"))
                .andExpect(jsonPath("$.reference").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Eliminación y verificación posterior
        mockMvc.perform(delete(path)).andExpect(status().isNoContent());
        mockMvc.perform(get(path)).andExpect(status().isNotFound());
        mockMvc.perform(delete(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value(path));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("los datos inválidos se rechazan con 400 y no se persisten")
    void invalidDataIsNotPersisted() throws Exception {
        mockMvc.perform(post("/api/v1/recharges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\": \"123\", \"amount\": 100, \"paymentMethod\": \"CASH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("el esquema de Flyway coincide con la entidad y persiste los montos con 2 decimales")
    void persistsDecimalAmounts() throws Exception {
        create(CARD_A, "2500.50", "DAVIPLATA");

        mockMvc.perform(get("/api/v1/getRecharges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(2500.50))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("DAVIPLATA"));
    }

    @Test
    @DisplayName("el filtro por tarjeta inexistente devuelve una página vacía, no un error")
    void filterWithoutMatchesReturnsEmptyPage() throws Exception {
        create(CARD_A, "3000", "PSE");

        mockMvc.perform(get("/api/v1/getRecharges").param("cardNumber", "9999999999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
