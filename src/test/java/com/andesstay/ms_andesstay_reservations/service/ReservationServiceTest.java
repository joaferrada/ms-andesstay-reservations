package com.andesstay.ms_andesstay_reservations.service;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import com.andesstay.ms_andesstay_reservations.exception.InvalidStatusTransitionException;
import com.andesstay.ms_andesstay_reservations.exception.ReservationNotFoundException;
import com.andesstay.ms_andesstay_reservations.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationServiceTest {

    private ReservationRepository repository;
    private ReservationNotificationPublisher publisher;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReservationRepository.class);
        publisher = mock(ReservationNotificationPublisher.class);
        service = new ReservationService(repository, publisher);
        when(repository.save(any(ReservationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void crearSiempreParteEnCreadaYPublica() {
        ReservationEntity body = new ReservationEntity();
        body.setId(99L);
        body.setEstado(ReservationStatus.CHECKOUT);

        ReservationEntity creada = service.crear(body, "ana@andesstay.cl");

        assertThat(creada.getEstado()).isEqualTo(ReservationStatus.CREADA);
        assertThat(creada.getId()).isNull();
        assertThat(creada.getClienteUsername()).isEqualTo("ana@andesstay.cl");
        verify(publisher).publishStatusChanged(creada, null);
    }

    @Test
    void transicionValidaGuardaYPublica() {
        ReservationEntity r = reserva(1L, ReservationStatus.CREADA);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        ReservationEntity resultado = service.cambiarEstado(1L, ReservationStatus.CONFIRMADA);

        assertThat(resultado.getEstado()).isEqualTo(ReservationStatus.CONFIRMADA);
        verify(repository).save(r);
        verify(publisher).publishStatusChanged(r, ReservationStatus.CREADA);
    }

    @Test
    void checkinSinConfirmarLanza409YNoPublica() {
        when(repository.findById(1L)).thenReturn(Optional.of(reserva(1L, ReservationStatus.CREADA)));

        assertThatThrownBy(() -> service.cambiarEstado(1L, ReservationStatus.CHECKIN_PENDIENTE))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("CREADA -> CHECKIN_PENDIENTE");
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void reservaInexistenteLanza404() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(5L, ReservationStatus.CONFIRMADA))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    private static ReservationEntity reserva(Long id, ReservationStatus estado) {
        ReservationEntity r = new ReservationEntity();
        r.setId(id);
        r.setEstado(estado);
        return r;
    }
}
