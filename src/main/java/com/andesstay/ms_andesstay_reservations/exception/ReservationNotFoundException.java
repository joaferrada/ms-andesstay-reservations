package com.andesstay.ms_andesstay_reservations.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(Long id) {
        super("Reserva " + id + " no existe");
    }
}
