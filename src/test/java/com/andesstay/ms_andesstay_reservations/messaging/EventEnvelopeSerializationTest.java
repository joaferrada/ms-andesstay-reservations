package com.andesstay.ms_andesstay_reservations.messaging;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El JSON que publica reservations debe tener el formato que espera ms-andesstay-notify
 * (fechas ISO como texto, eventId UUID como texto, payload como objeto).
 */
class EventEnvelopeSerializationTest {

    @Test
    void envelopeSeSerializaConElContratoComun() {
        JsonMapper mapper = JsonMapper.builder().build();
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(mapper);

        ReservationEntity r = new ReservationEntity();
        r.setId(10L);
        r.setClienteUsername("ana@andesstay.cl");
        r.setHuespedNombre("Ana Rojas");
        r.setHuespedEmail("ana.rojas@correo.cl");
        r.setAlojamientoNombre("Cabana Volcan");
        r.setFechaInicio(LocalDate.of(2026, 12, 1));
        r.setFechaFin(LocalDate.of(2026, 12, 5));
        r.setPrecioTotal(250000.0);
        r.setEstado(ReservationStatus.CONFIRMADA);

        EventEnvelope<ReservationPayload> envelope = EventEnvelope.of(CommandType.VOUCHER_GENERAR.name(),
                "trace-1", "reservation-10", "ms-andesstay-reservations", ReservationPayload.from(r, ReservationStatus.CREADA));

        Message message = converter.toMessage(envelope, new MessageProperties());
        JsonNode json = mapper.readTree(message.getBody());

        assertThat(message.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(json.path("type").asString()).isEqualTo("VOUCHER_GENERAR");
        assertThat(json.path("eventId").isString()).isTrue();
        assertThat(json.path("timestamp").isString()).isTrue();
        assertThat(json.path("traceId").asString()).isEqualTo("trace-1");
        assertThat(json.path("correlationId").asString()).isEqualTo("reservation-10");
        assertThat(json.path("source").asString()).isEqualTo("ms-andesstay-reservations");
        assertThat(json.path("payload").path("reservationId").asLong()).isEqualTo(10L);
        // destinatario del correo y titular del voucher: el huesped, no el usuario que creo la reserva
        assertThat(json.path("payload").path("huespedEmail").asString()).isEqualTo("ana.rojas@correo.cl");
        assertThat(json.path("payload").path("huespedNombre").asString()).isEqualTo("Ana Rojas");
        assertThat(json.path("payload").path("fechaInicio").asString()).isEqualTo("2026-12-01");
        assertThat(json.path("payload").path("estado").asString()).isEqualTo("CONFIRMADA");
        assertThat(json.path("payload").path("estadoAnterior").asString()).isEqualTo("CREADA");
    }
}
