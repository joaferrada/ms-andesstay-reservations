package com.andesstay.ms_andesstay_reservations.messaging;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RabbitReservationNotificationPublisherTest {

    static final MessagingProperties PROPS = new MessagingProperties(
            "ms-andesstay-reservations",
            Duration.ofDays(7),
            new MessagingProperties.Exchanges("cmd.direct", "cmd.topic", "cmd.dead.dlx"),
            new MessagingProperties.Routes(
                    new MessagingProperties.Route("q.cmd.email", "q.cmd.email.dlq", "email.send", "email.*", "email.dead"),
                    new MessagingProperties.Route("q.cmd.housekeeping", "q.cmd.housekeeping.dlq", "housekeeping.ticket", "housekeeping.#", "housekeeping.dead"),
                    new MessagingProperties.Route("q.cmd.voucher", "q.cmd.voucher.dlq", "voucher.gen", "voucher.*", "voucher.dead")),
            new MessagingProperties.Publishing("housekeeping.ticket.preparacion", "housekeeping.ticket.limpieza"));

    private RabbitTemplate template;
    private RabbitReservationNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        template = mock(RabbitTemplate.class);
        publisher = new RabbitReservationNotificationPublisher(template, PROPS);
    }

    @Test
    void confirmadaPublicaEmailVoucherYHousekeepingPorAmbosExchanges() {
        publisher.publishStatusChanged(reserva(ReservationStatus.CONFIRMADA), ReservationStatus.CREADA);

        verify(template).convertAndSend(eq("cmd.direct"), eq("email.send"), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(template).convertAndSend(eq("cmd.direct"), eq("voucher.gen"), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(template).convertAndSend(eq("cmd.topic"), eq("housekeeping.ticket.preparacion"), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        verifyNoMoreInteractions(template);
    }

    @Test
    void enEstadiaNoPublicaNada() {
        publisher.publishStatusChanged(reserva(ReservationStatus.EN_ESTADIA), ReservationStatus.CHECKIN_PENDIENTE);
        verifyNoInteractions(template);
    }

    @Test
    void brokerCaidoNoPropagaExcepcion() {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));

        assertThatCode(() -> publisher.publishStatusChanged(reserva(ReservationStatus.CHECKOUT), ReservationStatus.EN_ESTADIA))
                .doesNotThrowAnyException();
        verify(template, times(3)).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void planDeComandosPorEstado() {
        assertThat(CommandType.planFor(ReservationStatus.CREADA)).containsExactly(CommandType.EMAIL_RESERVA_CREADA);
        assertThat(CommandType.planFor(ReservationStatus.CHECKOUT))
                .containsExactly(CommandType.EMAIL_CHECKOUT, CommandType.HOUSEKEEPING_LIMPIEZA, CommandType.BOLETA_GENERAR);
        assertThat(CommandType.planFor(ReservationStatus.CANCELADA)).containsExactly(CommandType.EMAIL_RESERVA_CANCELADA);
    }

    private static ReservationEntity reserva(ReservationStatus estado) {
        ReservationEntity r = new ReservationEntity();
        r.setId(1L);
        r.setClienteUsername("ana@andesstay.cl");
        r.setHuespedNombre("Ana Rojas");
        r.setHuespedEmail("ana.rojas@correo.cl");
        r.setAlojamientoNombre("Cabana Volcan");
        r.setEstado(estado);
        return r;
    }
}
