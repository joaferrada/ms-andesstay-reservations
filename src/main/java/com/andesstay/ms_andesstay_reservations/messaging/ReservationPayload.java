package com.andesstay.ms_andesstay_reservations.messaging;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;

import java.time.LocalDate;

/**
 * Payload de los comandos emitidos por reservations (email, voucher, housekeeping).
 * El destinatario de los correos y el titular del voucher/boleta son huespedEmail y huespedNombre.
 * clienteUsername se mantiene solo por compatibilidad con consumidores antiguos.
 */
public record ReservationPayload(
        Long reservationId,
        String huespedNombre,
        String huespedEmail,
        String clienteUsername,
        String alojamientoNombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Double precioTotal,
        ReservationStatus estado,
        ReservationStatus estadoAnterior) {

    public static ReservationPayload from(ReservationEntity r, ReservationStatus estadoAnterior) {
        return new ReservationPayload(r.getId(), r.getHuespedNombre(), r.getHuespedEmail(), r.getClienteUsername(),
                r.getAlojamientoNombre(), r.getFechaInicio(), r.getFechaFin(), r.getPrecioTotal(), r.getEstado(),
                estadoAnterior);
    }
}
