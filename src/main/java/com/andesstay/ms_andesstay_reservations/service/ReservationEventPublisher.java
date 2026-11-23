package com.andesstay.ms_andesstay_reservations.service;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;

/**
 * Puerto de salida del dominio para eventos de streaming (hechos que ya ocurrieron con una reserva).
 *
 * Es distinto de {@link ReservationNotificationPublisher}: aquel envía comandos a RabbitMQ
 * ("envía este email"); este publica hechos en Kafka ("la reserva 12 pasó a CONFIRMADA") para
 * que audit, report o cualquier otro consumidor los procese a su ritmo.
 *
 * Las implementaciones no deben lanzar excepciones: si el broker falla, la operación de negocio continúa.
 */
public interface ReservationEventPublisher {

    void reservationCreated(ReservationEntity reserva, String actor);

    void statusChanged(ReservationEntity reserva, ReservationStatus estadoAnterior, String actor);

    void reservationUpdated(ReservationEntity reserva, String actor);

    void reservationDeleted(ReservationEntity reserva, String actor);
}
