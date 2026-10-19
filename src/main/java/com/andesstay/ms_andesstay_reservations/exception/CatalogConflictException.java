package com.andesstay.ms_andesstay_reservations.exception;

/**
 * Catalog rechazo descontar el cupo (sin cupos, unidad inactiva o inexistente). Se responde 409.
 */
public class CatalogConflictException extends RuntimeException {

    public CatalogConflictException(String message) {
        super(message);
    }
}
