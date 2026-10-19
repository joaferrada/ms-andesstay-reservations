package com.andesstay.ms_andesstay_reservations.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados del ciclo de vida de una reserva y su maquina de estados.
 *
 * <pre>
 *   CREADA --> CONFIRMADA --> CHECKIN_PENDIENTE --> EN_ESTADIA --> CHECKOUT
 *     |            |                  |
 *     +------------+------------------+--> CANCELADA
 * </pre>
 *
 * Regla del caso: no existe check-in sin confirmacion previa
 * (CREADA no puede saltar a CHECKIN_PENDIENTE ni a EN_ESTADIA).
 */
public enum ReservationStatus {
    CREADA,
    CONFIRMADA,
    CHECKIN_PENDIENTE,
    EN_ESTADIA,
    CHECKOUT,
    CANCELADA;

    private static final Map<ReservationStatus, Set<ReservationStatus>> TRANSICIONES = new EnumMap<>(ReservationStatus.class);

    static {
        TRANSICIONES.put(CREADA, EnumSet.of(CONFIRMADA, CANCELADA));
        TRANSICIONES.put(CONFIRMADA, EnumSet.of(CHECKIN_PENDIENTE, CANCELADA));
        TRANSICIONES.put(CHECKIN_PENDIENTE, EnumSet.of(EN_ESTADIA, CANCELADA));
        TRANSICIONES.put(EN_ESTADIA, EnumSet.of(CHECKOUT));
        TRANSICIONES.put(CHECKOUT, EnumSet.noneOf(ReservationStatus.class));
        TRANSICIONES.put(CANCELADA, EnumSet.noneOf(ReservationStatus.class));
    }

    public Set<ReservationStatus> siguientesPermitidos() {
        return Collections.unmodifiableSet(TRANSICIONES.get(this));
    }

    public boolean puedeTransicionarA(ReservationStatus destino) {
        return destino != null && TRANSICIONES.get(this).contains(destino);
    }

    /**
     * Estados que ya tienen un cupo descontado en el catalogo (desde CONFIRMADA hasta EN_ESTADIA).
     * Al pasar de uno de ellos a CANCELADA o CHECKOUT el cupo se devuelve.
     */
    public boolean ocupaCupo() {
        return this == CONFIRMADA || this == CHECKIN_PENDIENTE || this == EN_ESTADIA;
    }
}
