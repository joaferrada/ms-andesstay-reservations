package com.andesstay.ms_andesstay_reservations.messaging;

import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;

import java.util.List;

/**
 * Comandos asincronos que emite reservations y el destino logico de cada uno.
 * El nombre del enum viaja como {@code type} en el {@link EventEnvelope}.
 */
public enum CommandType {
    EMAIL_RESERVA_CREADA(Destination.EMAIL),
    EMAIL_RESERVA_CONFIRMADA(Destination.EMAIL),
    EMAIL_RECORDATORIO_CHECKIN(Destination.EMAIL),
    EMAIL_CHECKOUT(Destination.EMAIL),
    EMAIL_RESERVA_CANCELADA(Destination.EMAIL),
    VOUCHER_GENERAR(Destination.VOUCHER),
    BOLETA_GENERAR(Destination.VOUCHER),
    HOUSEKEEPING_PREPARACION(Destination.HOUSEKEEPING_PREPARATION),
    HOUSEKEEPING_LIMPIEZA(Destination.HOUSEKEEPING_CLEANING);

    /**
     * Destino logico; {@link RabbitReservationNotificationPublisher} lo traduce a exchange + routing key.
     * EMAIL y VOUCHER van por cmd.direct; HOUSEKEEPING_* va por cmd.topic.
     */
    public enum Destination { EMAIL, VOUCHER, HOUSEKEEPING_PREPARATION, HOUSEKEEPING_CLEANING }

    private final Destination destination;

    CommandType(Destination destination) {
        this.destination = destination;
    }

    public Destination destination() {
        return destination;
    }

    /**
     * Comandos a emitir cuando una reserva entra en {@code estado}.
     * <ul>
     *   <li>CREADA: email</li>
     *   <li>CONFIRMADA: email + voucher + ticket de preparacion</li>
     *   <li>CHECKIN_PENDIENTE: email de recordatorio</li>
     *   <li>EN_ESTADIA: nada</li>
     *   <li>CHECKOUT: email + ticket de limpieza + boleta</li>
     *   <li>CANCELADA: email</li>
     * </ul>
     */
    public static List<CommandType> planFor(ReservationStatus estado) {
        return switch (estado) {
            case CREADA -> List.of(EMAIL_RESERVA_CREADA);
            case CONFIRMADA -> List.of(EMAIL_RESERVA_CONFIRMADA, VOUCHER_GENERAR, HOUSEKEEPING_PREPARACION);
            case CHECKIN_PENDIENTE -> List.of(EMAIL_RECORDATORIO_CHECKIN);
            case EN_ESTADIA -> List.of();
            case CHECKOUT -> List.of(EMAIL_CHECKOUT, HOUSEKEEPING_LIMPIEZA, BOLETA_GENERAR);
            case CANCELADA -> List.of(EMAIL_RESERVA_CANCELADA);
        };
    }
}
