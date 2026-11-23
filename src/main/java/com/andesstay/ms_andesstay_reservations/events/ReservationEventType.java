package com.andesstay.ms_andesstay_reservations.events;

/**
 * Tipos de evento que reservations publica en el tópico reservations.events.
 * Son hechos en pasado: describen algo que ya ocurrió con la reserva.
 */
public enum ReservationEventType {

    /** Se creó una reserva (siempre parte en CREADA). */
    RESERVATION_CREATED,

    /** Cambió el estado: CONFIRMADA, CHECKIN_PENDIENTE, EN_ESTADIA, CHECKOUT o CANCELADA. */
    RESERVATION_STATUS_CHANGED,

    /** Se modificaron los datos (fechas, alojamiento, precio) sin cambiar el estado. */
    RESERVATION_UPDATED,

    /** Se eliminó la reserva. */
    RESERVATION_DELETED
}
