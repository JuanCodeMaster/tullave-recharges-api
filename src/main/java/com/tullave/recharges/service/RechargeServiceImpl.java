package com.tullave.recharges.service;

import com.tullave.recharges.dto.PageResponse;
import com.tullave.recharges.dto.RechargeRequest;
import com.tullave.recharges.dto.RechargeResponse;
import com.tullave.recharges.exception.ResourceNotFoundException;
import com.tullave.recharges.mapper.RechargeMapper;
import com.tullave.recharges.model.Recharge;
import com.tullave.recharges.repository.RechargeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class RechargeServiceImpl implements RechargeService {

    private static final Logger log = LoggerFactory.getLogger(RechargeServiceImpl.class);

    private final RechargeRepository repository;
    private final RechargeMapper mapper;

    public RechargeServiceImpl(RechargeRepository repository, RechargeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public RechargeResponse create(RechargeRequest request) {
        log.info("Registrando recarga: card={} amount={} method={}",
                mask(request.cardNumber()), request.amount(), request.paymentMethod());

        Recharge saved = repository.save(mapper.toEntity(request));

        log.info("Recarga registrada: id={} reference={}", saved.getId(), saved.getReference());
        return mapper.toResponse(saved);
    }

    @Override
    public RechargeResponse findById(Long id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.recharge(id));
    }

    @Override
    public PageResponse<RechargeResponse> findAll(String cardNumber, Pageable pageable) {
        Page<Recharge> page = StringUtils.hasText(cardNumber)
                ? repository.findByCardNumber(cardNumber.trim(), pageable)
                : repository.findAll(pageable);

        log.debug("Consulta de recargas: card={} page={} size={} total={}",
                mask(cardNumber), pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());

        return PageResponse.from(page.map(mapper::toResponse));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Recharge recharge = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Intento de eliminar recarga inexistente: id={}", id);
                    return ResourceNotFoundException.recharge(id);
                });

        repository.delete(recharge);
        log.info("Recarga eliminada: id={} reference={}", id, recharge.getReference());
    }

    /**
     * Enmascara el número de tarjeta en los logs: se trata como dato sensible del usuario.
     */
    static String mask(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }
}
