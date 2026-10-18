package com.andesstay.ms_andesstay_reservations.exception;

import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    private final ReservationStatus actual;
    private final ReservationStatus solicitado;

    public InvalidStatusTransitionException(ReservationStatus actual, ReservationStatus solicitado) {
        super("Transicion invalida " + actual + " -> " + solicitado
                + ". Permitidas desde " + actual + ": " + actual.siguientesPermitidos());
        this.actual = actual;
        this.solicitado = solicitado;
    }

    public ReservationStatus getActual() { return actual; }
    public ReservationStatus getSolicitado() { return solicitado; }
}
