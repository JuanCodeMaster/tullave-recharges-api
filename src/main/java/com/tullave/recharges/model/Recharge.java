package com.tullave.recharges.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Recarga digital registrada sobre una tarjeta tuLlave.
 * <p>
 * Además de los atributos exigidos se agrega {@code reference}: un identificador público (UUID)
 * que puede entregarse al usuario como comprobante o cruzarse con la pasarela de pago sin exponer
 * el id secuencial interno.
 * <p>
 * El esquema (tabla, índices y constraints) lo gobierna Flyway; Hibernate solo lo valida al arrancar.
 */
@Entity
@Table(name = "recharges")
public class Recharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false, unique = true)
    private UUID reference;

    @Column(name = "card_number", nullable = false, length = 16)
    private String cardNumber;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Recharge() {
        // Constructor requerido por JPA
    }

    public Recharge(String cardNumber, BigDecimal amount, PaymentMethod paymentMethod) {
        this.cardNumber = cardNumber;
        // Se normaliza a 2 decimales para que la respuesta sea idéntica recién creada o leída desde la BD.
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.paymentMethod = paymentMethod;
    }

    @PrePersist
    void onCreate() {
        if (reference == null) {
            reference = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getReference() {
        return reference;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
