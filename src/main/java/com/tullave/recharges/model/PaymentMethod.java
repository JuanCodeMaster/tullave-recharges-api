package com.tullave.recharges.model;

/**
 * Medios de pago aceptados para una recarga digital.
 * Se persiste como texto (EnumType.STRING) para que agregar o reordenar valores
 * no corrompa los datos históricos.
 */
public enum PaymentMethod {
    PSE,
    NEQUI,
    DAVIPLATA,
    CREDIT_CARD
}
