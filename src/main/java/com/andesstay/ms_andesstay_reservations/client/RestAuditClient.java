package com.andesstay.ms_andesstay_reservations.client;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import com.andesstay.ms_andesstay_reservations.service.AuditClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * Adaptador REST de {@link AuditClient}: POST /api/audit/events con el JWT del usuario que hizo la accion.
 * Si audit no responde (o rechaza el evento) se registra un warning y la operacion sigue.
 */
@Component
public class RestAuditClient implements AuditClient {

    private static final Logger log = LoggerFactory.getLogger(RestAuditClient.class);

    private final RestClient restClient;

    public RestAuditClient(@Qualifier("auditRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void registrarCreacion(ReservationEntity reserva, String actor) {
        enviar(new AuditEvent("RESERVA_CREADA", reserva.getId(), actor, null, nombre(reserva.getEstado()),
                "alojamiento=" + reserva.getAlojamientoNombre() + ", huesped=" + reserva.getHuespedEmail()));
    }

    @Override
    public void registrarCambioEstado(ReservationEntity reserva, ReservationStatus estadoAnterior, String actor) {
        enviar(new AuditEvent("CAMBIO_ESTADO", reserva.getId(), actor, nombre(estadoAnterior),
                nombre(reserva.getEstado()), null));
    }

    @Override
    public void registrarEliminacion(ReservationEntity reserva, String actor) {
        enviar(new AuditEvent("RESERVA_ELIMINADA", reserva.getId(), actor, nombre(reserva.getEstado()), null, null));
    }

    private void enviar(AuditEvent evento) {
        try {
            restClient.post()
                    .uri("/api/audit/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(evento)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("[AUDIT] no se pudo registrar tipo={} reservationId={}: {}",
                    evento.tipo(), evento.reservationId(), ex.getMessage());
        }
    }

    private static String nombre(ReservationStatus estado) {
        return estado != null ? estado.name() : null;
    }

    /** Body de POST /api/audit/events. occurredAt = instante de la accion en reservations. */
    record AuditEvent(String tipo, Long reservationId, String actor, String estadoAnterior,
                      String estadoNuevo, String detalle, Instant occurredAt) {

        AuditEvent(String tipo, Long reservationId, String actor, String estadoAnterior,
                   String estadoNuevo, String detalle) {
            this(tipo, reservationId, actor, estadoAnterior, estadoNuevo, detalle, Instant.now());
        }
    }
}
