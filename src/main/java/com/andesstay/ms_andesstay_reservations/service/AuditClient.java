package com.andesstay.ms_andesstay_reservations.service;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;

/**
 * Puerto de salida hacia ms-andesstay-audit (timeline de la reserva).
 *
 * Las implementaciones NO deben lanzar excepciones: si audit no responde se registra un
 * warning y la operacion de negocio sigue.
 *
 * @see com.andesstay.ms_andesstay_reservations.client.RestAuditClient
 */
public interface AuditClient {

    /** RESERVA_CREADA. */
    void registrarCreacion(ReservationEntity reserva, String actor);

    /** CAMBIO_ESTADO con estado anterior y nuevo. */
    void registrarCambioEstado(ReservationEntity reserva, ReservationStatus estadoAnterior, String actor);

    /** RESERVA_ELIMINADA. */
    void registrarEliminacion(ReservationEntity reserva, String actor);
}
