package com.tullave.recharges.exception;

/**
 * Se lanza cuando un recurso solicitado no existe. El handler global la traduce a 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException recharge(Long id) {
        return new ResourceNotFoundException("No existe una recarga con id " + id);
    }
}
