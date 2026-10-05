package com.tullave.recharges.service;

import com.tullave.recharges.dto.PageResponse;
import com.tullave.recharges.dto.RechargeRequest;
import com.tullave.recharges.dto.RechargeResponse;
import org.springframework.data.domain.Pageable;

/**
 * Casos de uso de recargas. El controlador depende de esta interfaz, no de la implementación.
 */
public interface RechargeService {

    RechargeResponse create(RechargeRequest request);

    RechargeResponse findById(Long id);

    PageResponse<RechargeResponse> findAll(String cardNumber, Pageable pageable);

    void delete(Long id);
}
