package com.tullave.recharges.mapper;

import com.tullave.recharges.dto.RechargeRequest;
import com.tullave.recharges.dto.RechargeResponse;
import com.tullave.recharges.model.Recharge;
import org.springframework.stereotype.Component;

/**
 * Conversión explícita entre DTOs y entidad. Con dos DTOs no se justifica MapStruct;
 * si el modelo crece, este componente es el único punto a migrar.
 */
@Component
public class RechargeMapper {

    public Recharge toEntity(RechargeRequest request) {
        return new Recharge(request.cardNumber(), request.amount(), request.paymentMethod());
    }

    public RechargeResponse toResponse(Recharge recharge) {
        return new RechargeResponse(
                recharge.getId(),
                recharge.getReference(),
                recharge.getCardNumber(),
                recharge.getAmount(),
                recharge.getPaymentMethod(),
                recharge.getCreatedAt()
        );
    }
}
