package com.andesstay.ms_andesstay_reservations.messaging;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;

import java.time.LocalDate;

/**
 * Payload de los comandos emitidos por reservations.
 */
public record ReservationPayload(
        Long reservationId,
        String clienteUsername,
        String alojamientoNombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Double precioTotal,
        ReservationStatus estado,
        ReservationStatus estadoAnterior) {

    public static ReservationPayload from(ReservationEntity r, ReservationStatus estadoAnterior) {
        return new ReservationPayload(r.getId(), r.getClienteUsername(), r.getAlojamientoNombre(),
                r.getFechaInicio(), r.getFechaFin(), r.getPrecioTotal(), r.getEstado(), estadoAnterior);
    }
}
