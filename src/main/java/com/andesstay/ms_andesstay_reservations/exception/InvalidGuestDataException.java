package com.andesstay.ms_andesstay_reservations.exception;

/**
 * Faltan o son invalidos los datos del huesped al crear una reserva (400).
 */
public class InvalidGuestDataException extends RuntimeException {

    public InvalidGuestDataException(String message) {
        super(message);
    }
}
