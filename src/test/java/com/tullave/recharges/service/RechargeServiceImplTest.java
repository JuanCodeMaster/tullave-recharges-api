package com.tullave.recharges.service;

import com.tullave.recharges.dto.PageResponse;
import com.tullave.recharges.dto.RechargeRequest;
import com.tullave.recharges.dto.RechargeResponse;
import com.tullave.recharges.exception.ResourceNotFoundException;
import com.tullave.recharges.mapper.RechargeMapper;
import com.tullave.recharges.model.PaymentMethod;
import com.tullave.recharges.model.Recharge;
import com.tullave.recharges.repository.RechargeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RechargeServiceImpl")
class RechargeServiceImplTest {

    private static final String CARD = "1010000012345678";

    @Mock
    private RechargeRepository repository;

    @Spy
    private RechargeMapper mapper = new RechargeMapper();

    @InjectMocks
    private RechargeServiceImpl service;

    private static Recharge persisted(Long id, String card, String amount) {
        Recharge recharge = new Recharge(card, new BigDecimal(amount), PaymentMethod.NEQUI);
        ReflectionTestUtils.setField(recharge, "id", id);
        ReflectionTestUtils.setField(recharge, "reference", UUID.randomUUID());
        ReflectionTestUtils.setField(recharge, "createdAt", Instant.parse("2026-10-04T12:00:00Z"));
        return recharge;
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("persiste la entidad construida desde el DTO y devuelve la respuesta mapeada")
        void createsAndMapsResponse() {
            var request = new RechargeRequest(CARD, new BigDecimal("50000"), PaymentMethod.NEQUI);
            when(repository.save(any(Recharge.class))).thenReturn(persisted(1L, CARD, "50000"));

            RechargeResponse response = service.create(request);

            ArgumentCaptor<Recharge> captor = ArgumentCaptor.forClass(Recharge.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getCardNumber()).isEqualTo(CARD);
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo("50000");
            assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.NEQUI);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.reference()).isNotNull();
            assertThat(response.cardNumber()).isEqualTo(CARD);
            assertThat(response.amount()).isEqualByComparingTo("50000");
            assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.NEQUI);
            assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-10-04T12:00:00Z"));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("devuelve la recarga cuando existe")
        void returnsWhenExists() {
            when(repository.findById(7L)).thenReturn(Optional.of(persisted(7L, CARD, "2000")));

            RechargeResponse response = service.findById(7L);

            assertThat(response.id()).isEqualTo(7L);
            assertThat(response.amount()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException cuando no existe")
        void throwsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("sin filtro consulta todas las recargas")
        void withoutFilterQueriesAll() {
            when(repository.findAll(pageable)).thenReturn(new PageImpl<>(
                    List.of(persisted(1L, CARD, "2000"), persisted(2L, "2020000012345678", "3000")), pageable, 2));

            PageResponse<RechargeResponse> page = service.findAll(null, pageable);

            verify(repository, never()).findByCardNumber(any(), any());
            assertThat(page.content()).hasSize(2);
            assertThat(page.totalElements()).isEqualTo(2);
            assertThat(page.page()).isZero();
            assertThat(page.size()).isEqualTo(10);
            assertThat(page.first()).isTrue();
            assertThat(page.last()).isTrue();
        }

        @Test
        @DisplayName("con filtro consulta por número de tarjeta (recortando espacios)")
        void withFilterQueriesByCard() {
            when(repository.findByCardNumber(eq(CARD), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(persisted(1L, CARD, "2000")), pageable, 1));

            PageResponse<RechargeResponse> page = service.findAll("  " + CARD + " ", pageable);

            verify(repository, never()).findAll(any(Pageable.class));
            assertThat(page.content()).extracting(RechargeResponse::cardNumber).containsExactly(CARD);
        }

        @Test
        @DisplayName("un filtro en blanco se trata como ausencia de filtro")
        void blankFilterIsIgnored() {
            when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

            PageResponse<RechargeResponse> page = service.findAll("   ", pageable);

            verify(repository, never()).findByCardNumber(any(), any());
            assertThat(page.content()).isEmpty();
            assertThat(page.totalPages()).isZero();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("elimina la recarga cuando existe")
        void deletesWhenExists() {
            Recharge existing = persisted(5L, CARD, "2000");
            when(repository.findById(5L)).thenReturn(Optional.of(existing));

            service.delete(5L);

            verify(repository).delete(existing);
        }

        @Test
        @DisplayName("lanza ResourceNotFoundException y no borra nada cuando no existe")
        void throwsWhenMissing() {
            when(repository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(42L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("42");

            verify(repository, never()).delete(any(Recharge.class));
            verify(repository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("mask")
    class Mask {

        @Test
        @DisplayName("solo deja visibles los últimos 4 dígitos")
        void masksAllButLastFour() {
            assertThat(RechargeServiceImpl.mask(CARD)).isEqualTo("****5678");
        }

        @Test
        @DisplayName("tolera nulos y valores cortos")
        void handlesNullAndShort() {
            assertThat(RechargeServiceImpl.mask(null)).isEqualTo("****");
            assertThat(RechargeServiceImpl.mask("12")).isEqualTo("****");
        }
    }
}
