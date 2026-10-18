package com.andesstay.ms_andesstay_reservations.service;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;

/**
 * Puerto de salida del dominio para comandos asincronos (email, voucher, housekeeping).
 *
 * El dominio solo conoce esta interfaz; la implementacion con RabbitMQ vive en el paquete
 * {@code messaging}. Las implementaciones NO deben lanzar excepciones: si el broker no esta
 * disponible, la reserva igual debe quedar guardada.
 */
public interface ReservationNotificationPublisher {

    /**
     * Notifica que la reserva quedo en {@code reserva.getEstado()}.
     *
     * @param estadoAnterior estado previo, o {@code null} si la reserva se acaba de crear
     */
    void publishStatusChanged(ReservationEntity reserva, ReservationStatus estadoAnterior);
}
