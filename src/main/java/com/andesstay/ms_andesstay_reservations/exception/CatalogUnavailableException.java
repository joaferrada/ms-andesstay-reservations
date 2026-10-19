package com.andesstay.ms_andesstay_reservations.exception;

/**
 * Catalog no responde y no se puede descontar el cupo. Se responde 503.
 */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
