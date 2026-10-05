package com.tullave.recharges.repository;

import com.tullave.recharges.model.Recharge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RechargeRepository extends JpaRepository<Recharge, Long> {

    Page<Recharge> findByCardNumber(String cardNumber, Pageable pageable);
}
